package com.forgeai.task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.forgeai.ai.AiClient;
import com.forgeai.ai.AiException;
import com.forgeai.ai.AiFailure;
import com.forgeai.ai.AiRequest;
import com.forgeai.ai.AiResult;
import com.forgeai.ai.PromptTemplates;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asks the AI for backlog tasks and repairs what comes back: only real links, dependencies that
 * exist and form no cycle, estimates on the scale, and tasks ordered after their dependencies.
 * New tasks are numbered from {@code firstSeq} in the final order.
 */
@Component
class TaskAssistant {

    static final int MAX_TASKS = 60;

    private final AiClient ai;
    private final PromptTemplates prompts;
    private final JsonMapper jsonMapper;

    TaskAssistant(AiClient ai, PromptTemplates prompts, JsonMapper jsonMapper) {
        this.ai = ai;
        this.prompts = prompts;
        this.jsonMapper = jsonMapper;
    }

    /** Prompt sections, already rendered as text. */
    record Inputs(String projectName, String architecture, String database, String api, String requirements, String existingTasks) {
    }

    record NewTask(int seqNumber, TaskContent content) {
    }

    record Generated(List<NewTask> tasks, String model) {
    }

    /**
     * @param existing codes of the tasks that stay, mapped to what they depend on
     */
    Generated generate(Inputs inputs, TaskRules.Allowed allowed, Map<String, List<String>> existing, int firstSeq) {
        String user = prompts.render("tasks-generate", Map.of(
                "projectName", inputs.projectName(),
                "architecture", inputs.architecture(),
                "database", inputs.database(),
                "api", inputs.api(),
                "requirements", inputs.requirements(),
                "existingTasks", inputs.existingTasks()));
        AiResult result = ai.complete(new AiRequest("tasks.generate", prompts.render("tasks-system", Map.of()), user, TaskSchemas.GENERATED));
        Payload payload;
        try {
            payload = jsonMapper.readValue(result.text(), Payload.class);
        } catch (JacksonException ex) {
            throw new AiException(AiFailure.BAD_RESPONSE, "The model returned JSON that does not match the tasks schema", ex);
        }
        List<NewTask> tasks = repair(payload, allowed, existing, firstSeq);
        if (tasks.isEmpty() && existing.isEmpty()) {
            throw new AiException(AiFailure.BAD_RESPONSE, "The model returned no usable tasks");
        }
        return new Generated(tasks, result.model());
    }

    record Payload(List<Item> tasks) {
    }

    record Item(String ref, String title, String description, String type, Integer estimate, List<String> acceptanceCriteria,
            List<String> dependsOn, List<String> requirements, List<String> entities, List<String> endpoints, List<String> components) {
    }

    /** A task during repair, identified by a temporary key until it is numbered. */
    private record Draft(String key, TaskContent content, List<String> dependsOn) {
    }

    static List<NewTask> repair(Payload payload, TaskRules.Allowed allowed, Map<String, List<String>> existing, int firstSeq) {
        List<Item> items = (payload.tasks() == null ? List.<Item>of() : payload.tasks()).stream()
                .filter(i -> i != null && !isBlank(i.title()) && !isBlank(i.description()))
                .limit(MAX_TASKS)
                .toList();

        // Temporary keys; the model's refs point at them.
        Map<String, String> keyByRef = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            String ref = items.get(i).ref();
            if (ref != null && !ref.isBlank()) keyByRef.putIfAbsent(ref.strip(), "#" + i);
        }

        // Dependencies are accepted in order, never creating a cycle (existing tasks included).
        Map<String, List<String>> graph = new HashMap<>(existing);
        List<Draft> drafts = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            String key = "#" + i;
            List<String> accepted = new ArrayList<>();
            for (String raw : nonNull(item.dependsOn())) {
                if (raw == null) continue;
                String target = keyByRef.containsKey(raw.strip()) ? keyByRef.get(raw.strip()) : raw.strip().toUpperCase(Locale.ROOT);
                boolean known = target.startsWith("#") || existing.containsKey(target);
                if (!known || target.equals(key) || accepted.contains(target) || TaskRules.reaches(target, key, graph)) continue;
                accepted.add(target);
            }
            graph.put(key, accepted);
            drafts.add(new Draft(key, new TaskContent(
                    truncate(item.title(), 200),
                    truncate(item.description(), 4_000),
                    parseType(item.type()),
                    estimate(item.estimate()),
                    new TaskDetails(
                            nonNull(item.acceptanceCriteria()).stream().filter(c -> !isBlank(c)).limit(10).map(c -> truncate(c, 500)).toList(),
                            List.of(), // filled in once numbered
                            filter(item.requirements(), code -> code.toUpperCase(Locale.ROOT), allowed.requirements(), 50),
                            filter(item.entities(), Function.identity(), allowed.entities(), 30),
                            filter(item.endpoints(), TaskRules::normaliseEndpoint, allowed.endpoints(), 30),
                            filter(item.components(), Function.identity(), allowed.components(), 20))), accepted));
        }

        // Stable topological order: a task comes after the new tasks it depends on.
        List<Draft> ordered = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        List<Draft> remaining = new ArrayList<>(drafts);
        while (!remaining.isEmpty()) {
            Draft next = remaining.stream()
                    .filter(d -> d.dependsOn().stream().noneMatch(dep -> dep.startsWith("#") && !placed.contains(dep)))
                    .findFirst()
                    .orElse(remaining.getFirst()); // unreachable: cycles were refused above
            remaining.remove(next);
            placed.add(next.key());
            ordered.add(next);
        }

        // Number in the final order and rewrite dependencies to codes.
        Map<String, String> codeByKey = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) codeByKey.put(ordered.get(i).key(), Task.code(firstSeq + i));
        List<NewTask> result = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            Draft draft = ordered.get(i);
            List<String> dependsOn = draft.dependsOn().stream().map(dep -> codeByKey.getOrDefault(dep, dep)).toList();
            TaskContent c = draft.content();
            result.add(new NewTask(firstSeq + i, new TaskContent(c.title(), c.description(), c.type(), c.estimate(),
                    c.details().withDependsOn(dependsOn))));
        }
        return result;
    }

    /** Nearest point value on the scale; null if missing or not positive. */
    static Integer estimate(Integer raw) {
        if (raw == null || raw < 1) return null;
        Integer best = null;
        for (int value : TaskRules.ESTIMATES) {
            if (best == null || Math.abs(value - raw) <= Math.abs(best - raw)) best = value;
        }
        return best;
    }

    private static TaskType parseType(String value) {
        if (value == null) return TaskType.BACKEND;
        try {
            return TaskType.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return TaskType.BACKEND;
        }
    }

    private static List<String> filter(List<String> values, Function<String, String> normalise, Set<String> allowed, int limit) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : nonNull(values)) {
            if (value == null || value.isBlank()) continue;
            String v = normalise.apply(value.strip());
            if (allowed.contains(v)) result.add(v);
            if (result.size() == limit) break;
        }
        return List.copyOf(result);
    }

    static String renderExisting(List<TaskRules.Item> items, Map<String, Task> tasks) {
        if (items.isEmpty()) return "(none)";
        StringBuilder text = new StringBuilder();
        for (TaskRules.Item item : items) {
            Task task = Objects.requireNonNull(tasks.get(item.code()));
            text.append(item.code()).append(" [").append(task.getStatus()).append(", ").append(task.getType()).append("] ")
                    .append(task.getTitle());
            if (!item.details().dependsOn().isEmpty()) {
                text.append(" (after ").append(String.join(", ", item.details().dependsOn())).append(')');
            }
            if (!item.details().endpoints().isEmpty()) {
                text.append(" — implements ").append(String.join(", ", item.details().endpoints()));
            }
            text.append('\n');
        }
        return text.toString().strip();
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String truncate(String text, int max) {
        String stripped = text.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max).strip();
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}

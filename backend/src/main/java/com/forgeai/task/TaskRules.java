package com.forgeai.task;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.forgeai.design.DesignDtos.Area;
import com.forgeai.design.DesignDtos.Finding;
import com.forgeai.task.TaskDtos.SaveTask;

/**
 * Rules for tasks.
 *
 * <ul>
 *   <li>{@link #problems}: mistakes that block saving: an estimate off the scale, dependencies on
 *       tasks that do not exist or that would form a cycle, links to things that do not exist.</li>
 *   <li>{@link #findings}: what the review should look at: links to design elements that have
 *       since been removed, tasks ordered before their dependencies, and design elements or
 *       requirements no task implements.</li>
 * </ul>
 */
final class TaskRules {

    static final List<Integer> ESTIMATES = List.of(1, 2, 3, 5, 8, 13);

    private TaskRules() {
    }

    /** What tasks may link to right now. */
    record Allowed(Set<String> requirements, Set<String> entities, Set<String> endpoints, Set<String> components) {
    }

    /** A task as the checks see it. */
    record Item(String code, int position, TaskDetails details) {
    }

    static TaskContent tidy(SaveTask request) {
        return new TaskContent(
                request.title().strip(),
                request.description().strip(),
                request.type(),
                request.estimate(),
                new TaskDetails(
                        distinct(request.acceptanceCriteria(), Function.identity()),
                        distinct(request.dependsOn(), code -> code.toUpperCase(Locale.ROOT)),
                        distinct(request.requirements(), code -> code.toUpperCase(Locale.ROOT)),
                        distinct(request.entities(), Function.identity()),
                        distinct(request.endpoints(), TaskRules::normaliseEndpoint),
                        distinct(request.components(), Function.identity())));
    }

    /**
     * Problems a person has to fix before the task can be saved; empty when it is consistent.
     *
     * @param selfCode     the task's code, or null for a new task
     * @param dependencies every other task's code mapped to the codes it depends on
     * @param previous     the task's details before this edit (links that existed may stay even if
     *                     the design has since changed), or null for a new task
     */
    static List<String> problems(TaskContent content, String selfCode, Map<String, List<String>> dependencies,
            Allowed allowed, TaskDetails previous) {
        List<String> problems = new ArrayList<>();
        if (content.estimate() != null && !ESTIMATES.contains(content.estimate())) {
            problems.add("The estimate must be one of 1, 2, 3, 5, 8 or 13 points.");
        }
        TaskDetails details = content.details();
        for (String code : details.dependsOn()) {
            if (code.equals(selfCode)) {
                problems.add("A task cannot depend on itself.");
            } else if (!dependencies.containsKey(code)) {
                problems.add(code + " is not a task of this project.");
            } else if (selfCode != null && reaches(code, selfCode, dependencies)) {
                problems.add(code + " already depends on " + selfCode + " (directly or through other tasks), so "
                        + selfCode + " cannot depend on " + code + ".");
            }
        }
        TaskDetails before = previous == null ? new TaskDetails(null, null, null, null, null, null) : previous;
        unknown(details.requirements(), allowed.requirements(), before.requirements())
                .forEach(code -> problems.add(code + " is not a requirement of this project."));
        unknown(details.entities(), allowed.entities(), before.entities())
                .forEach(name -> problems.add(name + " is not an entity of the database design."));
        unknown(details.endpoints(), allowed.endpoints(), before.endpoints())
                .forEach(endpoint -> problems.add(endpoint + " is not an endpoint of the API design."));
        unknown(details.components(), allowed.components(), before.components())
                .forEach(name -> problems.add(name + " is not a component of the architecture."));
        return problems;
    }

    /** True if {@code from} depends on {@code target}, directly or through other tasks. */
    static boolean reaches(String from, String target, Map<String, List<String>> dependencies) {
        Deque<String> todo = new ArrayDeque<>(List.of(from));
        Set<String> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            String code = todo.pop();
            if (code.equals(target)) return true;
            if (seen.add(code)) todo.addAll(dependencies.getOrDefault(code, List.of()));
        }
        return false;
    }

    /**
     * Review checks over the whole backlog.
     *
     * @param functionalRequirements approved functional requirement codes mapped to their titles
     */
    static List<Finding> findings(List<Item> items, Allowed current, Map<String, String> functionalRequirements) {
        List<Finding> findings = new ArrayList<>();
        if (items.isEmpty()) return findings;
        Map<String, Integer> positions = new HashMap<>();
        items.forEach(item -> positions.put(item.code(), item.position()));

        Set<String> entities = new HashSet<>();
        Set<String> endpoints = new HashSet<>();
        Set<String> requirements = new HashSet<>();
        for (Item item : items) {
            TaskDetails d = item.details();
            entities.addAll(d.entities());
            endpoints.addAll(d.endpoints());
            requirements.addAll(d.requirements());
            for (String endpoint : d.endpoints()) {
                if (!current.endpoints().contains(endpoint)) {
                    findings.add(Finding.error(Area.TASKS, item.code() + " links to " + endpoint + ", which is no longer in the API design."));
                }
            }
            for (String entity : d.entities()) {
                if (!current.entities().contains(entity)) {
                    findings.add(Finding.error(Area.TASKS, item.code() + " links to the entity " + entity
                            + ", which is no longer in the database design."));
                }
            }
            for (String component : d.components()) {
                if (!current.components().contains(component)) {
                    findings.add(Finding.warning(Area.TASKS, item.code() + " links to the component " + component
                            + ", which is no longer in the architecture."));
                }
            }
            for (String dependency : d.dependsOn()) {
                Integer at = positions.get(dependency);
                if (at != null && at > item.position()) {
                    findings.add(Finding.warning(Area.TASKS, item.code() + " comes before " + dependency + ", which it depends on."));
                }
            }
        }
        current.endpoints().stream().sorted().filter(e -> !endpoints.contains(e))
                .forEach(e -> findings.add(Finding.warning(Area.TASKS, "No task implements " + e + ".")));
        current.entities().stream().sorted().filter(e -> !entities.contains(e))
                .forEach(e -> findings.add(Finding.warning(Area.TASKS, "No task covers the entity " + e + ".")));
        functionalRequirements.forEach((code, title) -> {
            if (!requirements.contains(code)) {
                findings.add(Finding.warning(Area.REQUIREMENTS, "No task implements " + code + " (" + title + ")."));
            }
        });
        return findings;
    }

    /** "get  /invoices/" → "GET /invoices". */
    static String normaliseEndpoint(String endpoint) {
        String[] parts = endpoint.strip().split("\\s+", 2);
        if (parts.length < 2) return endpoint.strip();
        String path = parts[1].strip();
        while (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return parts[0].toUpperCase(Locale.ROOT) + " " + path;
    }

    private static List<String> unknown(List<String> values, Set<String> allowed, List<String> before) {
        return values.stream().filter(v -> !allowed.contains(v) && !before.contains(v)).toList();
    }

    private static List<String> distinct(List<String> values, Function<String, String> normalise) {
        Set<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) seen.add(normalise.apply(value.strip()));
        }
        return List.copyOf(seen);
    }
}

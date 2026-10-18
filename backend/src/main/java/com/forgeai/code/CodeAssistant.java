package com.forgeai.code;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.forgeai.ai.AiClient;
import com.forgeai.ai.AiException;
import com.forgeai.ai.AiFailure;
import com.forgeai.ai.AiRequest;
import com.forgeai.ai.AiResult;
import com.forgeai.ai.PromptTemplates;
import com.forgeai.task.TaskService.CodeTask;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asks the AI for the code of one task, with the approved design and the accepted workspace as
 * context, and repairs the proposal: only safe paths, actions that match the workspace (an
 * "update" of a missing file becomes a "create" and so on), no unchanged files, no duplicates.
 */
@Component
class CodeAssistant {

    /** Code is long: allow a bigger answer than the other stages. */
    static final int MAX_OUTPUT_TOKENS = 32_000;
    static final int MAX_FILES = 25;
    static final int MAX_FILE_CHARS = 200_000;
    /** How much existing code is shown to the model, in characters. */
    static final int CONTEXT_BUDGET = 80_000;

    private final AiClient ai;
    private final PromptTemplates prompts;
    private final JsonMapper jsonMapper;

    CodeAssistant(AiClient ai, PromptTemplates prompts, JsonMapper jsonMapper) {
        this.ai = ai;
        this.prompts = prompts;
        this.jsonMapper = jsonMapper;
    }

    /** The design as prompt text. */
    record Design(String projectName, String architecture, String database, String api) {
    }

    /** A workspace file as context: path, content, and the task that last wrote it. */
    record WorkspaceFile(String path, String content, String lastTaskCode, java.time.Instant updatedAt) {
    }

    record Proposed(String path, ChangeAction action, String baseContent, String newContent, String explanation) {
    }

    record Generated(String summary, List<Proposed> changes, String model) {
    }

    Generated generate(Design design, CodeTask task, Map<String, String> requirementTitles, List<WorkspaceFile> workspace) {
        Context context = context(task, workspace);
        String user = prompts.render("code-generate", Map.of(
                "projectName", design.projectName(),
                "architecture", design.architecture(),
                "database", design.database(),
                "api", design.api(),
                "task", renderTask(task, requirementTitles),
                "workspace", context.listing(),
                "files", context.contents()));

        AiResult result = ai.complete(new AiRequest("code.generate", prompts.render("code-system", Map.of()), user,
                CodeSchemas.GENERATED, MAX_OUTPUT_TOKENS));
        Payload payload;
        try {
            payload = jsonMapper.readValue(result.text(), Payload.class);
        } catch (JacksonException ex) {
            throw new AiException(AiFailure.BAD_RESPONSE, "The model returned JSON that does not match the code schema", ex);
        }
        Map<String, String> current = new LinkedHashMap<>();
        workspace.forEach(f -> current.put(f.path(), f.content()));
        List<Proposed> changes = repair(payload, current);
        if (changes.isEmpty()) {
            throw new AiException(AiFailure.BAD_RESPONSE, "The model proposed no usable file changes for " + task.code());
        }
        String summary = payload.summary() == null || payload.summary().isBlank()
                ? "Code for " + task.code() + "." : truncate(payload.summary(), 4_000);
        return new Generated(summary, changes, result.model());
    }

    record Payload(String summary, List<FileItem> files) {
    }

    record FileItem(String path, String action, String content, String explanation) {
    }

    static List<Proposed> repair(Payload payload, Map<String, String> current) {
        List<Proposed> changes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (FileItem item : payload.files() == null ? List.<FileItem>of() : payload.files()) {
            if (item == null || changes.size() == MAX_FILES) continue;
            String path = CodePaths.normalise(item.path());
            if (path == null || !seen.add(path)) continue;
            ChangeAction action = parse(item.action());
            String base = current.get(path);
            String content = item.content() == null ? "" : stripFence(item.content());
            String explanation = item.explanation() == null || item.explanation().isBlank()
                    ? "Changed for this task." : truncate(item.explanation(), 1_000);

            if (action == ChangeAction.DELETE) {
                if (base != null) changes.add(new Proposed(path, ChangeAction.DELETE, base, null, explanation));
                continue;
            }
            if (content.isBlank() || content.length() > MAX_FILE_CHARS) continue;
            if (!content.endsWith("\n")) content = content + "\n";
            if (base == null) {
                changes.add(new Proposed(path, ChangeAction.CREATE, null, content, explanation));
            } else if (!base.equals(content)) {
                changes.add(new Proposed(path, ChangeAction.UPDATE, base, content, explanation));
            }
        }
        return changes;
    }

    // --- Context -------------------------------------------------------------------

    record Context(String listing, String contents) {
    }

    /**
     * Lists every file and includes as much content as the budget allows: first the files written
     * by the tasks this one depends on, then the most recently changed ones.
     */
    static Context context(CodeTask task, List<WorkspaceFile> workspace) {
        if (workspace.isEmpty()) return new Context("(empty: this is the first code of the project)", "(none)");
        Set<String> dependencyCodes = new HashSet<>(task.details().dependsOn());
        List<WorkspaceFile> ordered = new ArrayList<>(workspace);
        ordered.sort(Comparator
                .comparing((WorkspaceFile f) -> !dependencyCodes.contains(f.lastTaskCode()))
                .thenComparing(WorkspaceFile::updatedAt, Comparator.reverseOrder()));

        Set<String> included = new HashSet<>();
        StringBuilder contents = new StringBuilder();
        int used = 0;
        for (WorkspaceFile file : ordered) {
            int size = file.content().length() + file.path().length() + 10;
            if (used + size > CONTEXT_BUDGET) continue;
            contents.append("=== ").append(file.path()).append(" ===\n").append(file.content());
            if (!file.content().endsWith("\n")) contents.append('\n');
            contents.append('\n');
            used += size;
            included.add(file.path());
        }
        StringBuilder listing = new StringBuilder();
        for (WorkspaceFile file : workspace) {
            listing.append(file.path()).append(" (").append(lines(file.content())).append(" lines");
            if (!included.contains(file.path())) listing.append(", content not shown");
            listing.append(")\n");
        }
        return new Context(listing.toString().strip(), contents.isEmpty() ? "(none)" : contents.toString().strip());
    }

    static String renderTask(CodeTask task, Map<String, String> requirementTitles) {
        StringBuilder text = new StringBuilder();
        text.append(task.code()).append(" [").append(task.type()).append("] ").append(task.title()).append('\n');
        text.append(task.description()).append('\n');
        if (!task.details().acceptanceCriteria().isEmpty()) {
            text.append("Acceptance criteria:\n");
            task.details().acceptanceCriteria().forEach(c -> text.append("- ").append(c).append('\n'));
        }
        if (!task.dependencies().isEmpty()) text.append("Builds on: ").append(String.join("; ", task.dependencies())).append('\n');
        if (!task.details().requirements().isEmpty()) {
            text.append("Requirements:\n");
            task.details().requirements().forEach(code ->
                    text.append("- ").append(code).append(": ").append(requirementTitles.getOrDefault(code, "")).append('\n'));
        }
        if (!task.details().entities().isEmpty()) text.append("Entities: ").append(String.join(", ", task.details().entities())).append('\n');
        if (!task.details().endpoints().isEmpty()) text.append("Endpoints: ").append(String.join(", ", task.details().endpoints())).append('\n');
        if (!task.details().components().isEmpty()) text.append("Components: ").append(String.join(", ", task.details().components())).append('\n');
        return text.toString().strip();
    }

    static int lines(String content) {
        if (content.isEmpty()) return 0;
        int count = (int) content.chars().filter(c -> c == '\n').count();
        return content.endsWith("\n") ? count : count + 1;
    }

    /** Removes a Markdown code fence wrapped around the whole file, which models sometimes add. */
    static String stripFence(String content) {
        String trimmed = content.strip();
        if (trimmed.startsWith("```") && trimmed.endsWith("```") && trimmed.length() > 6) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                String inner = trimmed.substring(firstNewline + 1, trimmed.length() - 3);
                return inner.endsWith("\n") ? inner : inner + "\n";
            }
        }
        return content;
    }

    private static ChangeAction parse(String value) {
        if (value == null) return ChangeAction.UPDATE;
        try {
            return ChangeAction.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ChangeAction.UPDATE;
        }
    }

    private static String truncate(String text, int max) {
        String stripped = text.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max).strip();
    }
}

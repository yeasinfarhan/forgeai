package com.forgeai.review;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
import com.forgeai.code.CodePaths;
import com.forgeai.code.CodeService.SourceFile;
import com.forgeai.task.TaskType;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asks the AI to review the accepted code against the approved design and common coding
 * standards, then repairs the findings: a file must be one that was shown, a line must exist in
 * it, a requirement must be approved, duplicates are dropped and the most severe come first.
 */
@Component
class ReviewAssistant {

    static final int MAX_OUTPUT_TOKENS = 16_000;
    static final int MAX_FINDINGS = 40;
    /** How much code is shown to the model, in characters. */
    static final int CODE_BUDGET = 150_000;

    private final AiClient ai;
    private final PromptTemplates prompts;
    private final JsonMapper jsonMapper;

    ReviewAssistant(AiClient ai, PromptTemplates prompts, JsonMapper jsonMapper) {
        this.ai = ai;
        this.prompts = prompts;
        this.jsonMapper = jsonMapper;
    }

    /** What the review is checked against, as prompt text. */
    record Design(String projectName, String requirements, String architecture, String database, String api, String tasks) {
    }

    record NewFinding(
            FindingSeverity severity,
            FindingCategory category,
            String path,
            Integer line,
            String title,
            String detail,
            String suggestion,
            String requirementCode,
            TaskType taskType) {
    }

    record Result(String summary, List<NewFinding> findings, String model, int filesReviewed, int filesTotal) {
    }

    Result review(Design design, List<SourceFile> files, Set<String> requirementCodes) {
        Shown shown = shown(files);
        String user = prompts.render("review-generate", Map.of(
                "projectName", design.projectName(),
                "requirements", design.requirements(),
                "architecture", design.architecture(),
                "database", design.database(),
                "api", design.api(),
                "tasks", design.tasks(),
                "workspace", shown.listing(),
                "files", shown.contents()));
        AiResult result = ai.complete(new AiRequest("review.generate", prompts.render("review-system", Map.of()), user,
                ReviewSchemas.REVIEW, MAX_OUTPUT_TOKENS));
        Payload payload;
        try {
            payload = jsonMapper.readValue(result.text(), Payload.class);
        } catch (JacksonException ex) {
            throw new AiException(AiFailure.BAD_RESPONSE, "The model returned JSON that does not match the review schema", ex);
        }
        if (payload.summary() == null || payload.summary().isBlank()) {
            throw new AiException(AiFailure.BAD_RESPONSE, "The model returned a review without a summary");
        }
        return new Result(truncate(payload.summary(), 4_000), repair(payload, shown.lines(), requirementCodes), result.model(),
                shown.lines().size(), files.size());
    }

    record Payload(String summary, List<Item> findings) {
    }

    record Item(String severity, String category, String path, Integer line, String title, String detail, String suggestion,
            String requirement, String taskType) {
    }

    /**
     * @param shownLines       the files the model saw, with their number of lines
     * @param requirementCodes the approved requirements
     */
    static List<NewFinding> repair(Payload payload, Map<String, Integer> shownLines, Set<String> requirementCodes) {
        List<NewFinding> findings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Item item : payload.findings() == null ? List.<Item>of() : payload.findings()) {
            if (item == null || isBlank(item.title()) || isBlank(item.detail())) continue;
            String path = item.path() == null ? null : CodePaths.normalise(item.path());
            if (path != null && !shownLines.containsKey(path)) path = null;
            Integer line = path != null && item.line() != null && item.line() >= 1 && item.line() <= shownLines.get(path)
                    ? item.line() : null;
            String title = truncate(item.title(), 200);
            if (!seen.add((path == null ? "" : path) + "|" + title.toLowerCase(Locale.ROOT))) continue;
            String requirement = item.requirement() == null ? null : item.requirement().strip().toUpperCase(Locale.ROOT);
            if (requirement != null && !requirementCodes.contains(requirement)) requirement = null;
            FindingCategory category = parse(FindingCategory.class, item.category(), FindingCategory.MAINTAINABILITY);
            findings.add(new NewFinding(
                    parse(FindingSeverity.class, item.severity(), FindingSeverity.MEDIUM),
                    category,
                    path,
                    line,
                    title,
                    truncate(item.detail(), 2_000),
                    isBlank(item.suggestion()) ? "Fix the problem described above." : truncate(item.suggestion(), 2_000),
                    requirement,
                    parse(TaskType.class, item.taskType(), category == FindingCategory.TESTING ? TaskType.TESTING : TaskType.BACKEND)));
        }
        findings.sort(Comparator.comparing(NewFinding::severity));
        return findings.size() > MAX_FINDINGS ? List.copyOf(findings.subList(0, MAX_FINDINGS)) : findings;
    }

    /** The files shown to the model: application code before tests, most recently changed first, within the budget. */
    record Shown(String listing, String contents, Map<String, Integer> lines) {
    }

    static Shown shown(List<SourceFile> files) {
        List<SourceFile> ordered = new ArrayList<>(files);
        ordered.sort(Comparator
                .comparing((SourceFile f) -> CodePaths.isTestFile(f.path()))
                .thenComparing(SourceFile::updatedAt, Comparator.reverseOrder()));
        Map<String, Integer> lines = new java.util.LinkedHashMap<>();
        StringBuilder contents = new StringBuilder();
        int used = 0;
        for (SourceFile file : ordered) {
            int size = file.content().length() + file.path().length() + 10;
            if (used + size > CODE_BUDGET) continue;
            contents.append("=== ").append(file.path()).append(" ===\n").append(numbered(file.content())).append('\n');
            used += size;
            lines.put(file.path(), lineCount(file.content()));
        }
        StringBuilder listing = new StringBuilder();
        for (SourceFile file : files) {
            listing.append(file.path()).append(" (").append(lineCount(file.content())).append(" lines");
            if (!lines.containsKey(file.path())) listing.append(", not shown: do not report on it");
            listing.append(")\n");
        }
        return new Shown(listing.toString().strip(), contents.isEmpty() ? "(none)" : contents.toString().strip(), lines);
    }

    /** File content with line numbers, so the model can point at lines. */
    static String numbered(String content) {
        StringBuilder text = new StringBuilder();
        String[] lines = content.split("\n", -1);
        int count = lineCount(content);
        for (int i = 0; i < count; i++) text.append(i + 1).append(" | ").append(lines[i]).append('\n');
        return text.toString();
    }

    static int lineCount(String content) {
        if (content.isEmpty()) return 0;
        int count = (int) content.chars().filter(c -> c == '\n').count();
        return content.endsWith("\n") ? count : count + 1;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static String truncate(String text, int max) {
        String stripped = text.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max).strip();
    }
}

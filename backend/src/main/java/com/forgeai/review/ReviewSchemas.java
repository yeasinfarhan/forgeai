package com.forgeai.review;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.forgeai.task.TaskType;

/** JSON schema for a code review (structured outputs; converted for Gemini automatically). */
final class ReviewSchemas {

    private ReviewSchemas() {
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private static final Map<String, Object> FINDING = Map.ofEntries(
            Map.entry("type", "object"),
            Map.entry("properties", Map.ofEntries(
                    Map.entry("severity", Map.of("type", "string", "enum", names(FindingSeverity.values()))),
                    Map.entry("category", Map.of("type", "string", "enum", names(FindingCategory.values()))),
                    Map.entry("path", Map.of("anyOf", List.of(Map.of("type", "string"), Map.of("type", "null")),
                            "description", "The file the finding is about, exactly as listed; null if it concerns the project as a whole.")),
                    Map.entry("line", Map.of("anyOf", List.of(Map.of("type", "integer"), Map.of("type", "null")),
                            "description", "The 1-based line the finding is about, or null.")),
                    Map.entry("title", Map.of("type", "string", "description", "The problem in one short sentence, at most 120 characters.")),
                    Map.entry("detail", Map.of("type", "string", "description", "What is wrong and why it matters, 1-4 sentences.")),
                    Map.entry("suggestion", Map.of("type", "string", "description", "Concretely how to fix it, 1-4 sentences.")),
                    Map.entry("requirement", Map.of("anyOf", List.of(Map.of("type", "string"), Map.of("type", "null")),
                            "description", "Code of the requirement concerned (e.g. FR-003), or null.")),
                    Map.entry("taskType", Map.of("type", "string", "enum", names(TaskType.values()),
                            "description", "The kind of task that would fix it.")))),
            Map.entry("required", List.of("severity", "category", "path", "line", "title", "detail", "suggestion", "requirement", "taskType")),
            Map.entry("additionalProperties", false));

    static final Map<String, Object> REVIEW = Map.of(
            "type", "object",
            "properties", Map.of(
                    "summary", Map.of("type", "string", "description", "Overall assessment of the code, 2-4 sentences."),
                    "findings", Map.of("type", "array", "items", FINDING)),
            "required", List.of("summary", "findings"),
            "additionalProperties", false);
}

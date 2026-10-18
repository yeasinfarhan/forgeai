package com.forgeai.task;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** JSON schema for generated tasks (structured outputs; converted for Gemini automatically). */
final class TaskSchemas {

    private TaskSchemas() {
    }

    private static Map<String, Object> strings(String description) {
        return Map.of("type", "array", "description", description, "items", Map.of("type", "string"));
    }

    private static final Map<String, Object> TASK = Map.of(
            "type", "object",
            "properties", Map.ofEntries(
                    Map.entry("ref", Map.of("type", "string", "description", "Your own short id for this task, e.g. \"N1\", used in dependsOn.")),
                    Map.entry("title", Map.of("type", "string", "description", "Imperative, under 10 words.")),
                    Map.entry("description", Map.of("type", "string", "description", "What to build and how, 1-3 sentences.")),
                    Map.entry("type", Map.of("type", "string", "enum", Arrays.stream(TaskType.values()).map(Enum::name).toList())),
                    Map.entry("estimate", Map.of("type", "integer", "description", "Story points: 1, 2, 3, 5, 8 or 13.")),
                    Map.entry("acceptanceCriteria", strings("2-5 concrete, verifiable checks.")),
                    Map.entry("dependsOn", strings("refs of new tasks, or codes of existing tasks (T-001), that must be done first.")),
                    Map.entry("requirements", strings("Requirement codes it implements, exactly as given.")),
                    Map.entry("entities", strings("Entity (table) names it touches, exactly as given.")),
                    Map.entry("endpoints", strings("Endpoints it implements, exactly as given, e.g. \"GET /invoices/{invoiceId}\".")),
                    Map.entry("components", strings("Architecture component names it belongs to, exactly as given."))),
            "required", List.of("ref", "title", "description", "type", "estimate", "acceptanceCriteria", "dependsOn",
                    "requirements", "entities", "endpoints", "components"),
            "additionalProperties", false);

    static final Map<String, Object> GENERATED = Map.of(
            "type", "object",
            "properties", Map.of("tasks", Map.of("type", "array", "items", TASK)),
            "required", List.of("tasks"),
            "additionalProperties", false);
}

package com.forgeai.requirement;

import java.util.List;
import java.util.Map;

/** JSON schema for generated requirements (structured outputs; converted for Gemini automatically). */
final class RequirementSchemas {

    private RequirementSchemas() {
    }

    private static final Map<String, Object> ITEM = Map.of(
            "type", "object",
            "properties", Map.of(
                    "type", Map.of("type", "string", "enum", List.of("FUNCTIONAL", "NON_FUNCTIONAL")),
                    "category", Map.of("type", "string",
                            "description", "Feature area for functional requirements; quality category for non-functional ones."),
                    "title", Map.of("type", "string", "description", "Short title, under 10 words."),
                    "description", Map.of("type", "string", "description", "One or two sentences."),
                    "acceptanceCriteria", Map.of("type", "array",
                            "description", "2-5 concrete, testable criteria.",
                            "items", Map.of("type", "string")),
                    "priority", Map.of("type", "string", "enum", List.of("MUST", "SHOULD", "COULD"))),
            "required", List.of("type", "category", "title", "description", "acceptanceCriteria", "priority"),
            "additionalProperties", false);

    static final Map<String, Object> GENERATED = Map.of(
            "type", "object",
            "properties", Map.of(
                    "requirements", Map.of("type", "array", "items", ITEM)),
            "required", List.of("requirements"),
            "additionalProperties", false);
}

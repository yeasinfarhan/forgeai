package com.forgeai.code;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** JSON schema for a generated change set (structured outputs; converted for Gemini automatically). */
final class CodeSchemas {

    private CodeSchemas() {
    }

    private static final Map<String, Object> FILE = Map.of(
            "type", "object",
            "properties", Map.of(
                    "path", Map.of("type", "string", "description", "Relative path from the repository root, e.g. \"backend/src/main/java/com/acme/InvoiceController.java\"."),
                    "action", Map.of("type", "string", "enum", Arrays.stream(ChangeAction.values()).map(Enum::name).toList()),
                    "content", Map.of("type", "string", "description", "The complete new file content; empty for DELETE."),
                    "explanation", Map.of("type", "string", "description", "One sentence on what changed in this file and why.")),
            "required", List.of("path", "action", "content", "explanation"),
            "additionalProperties", false);

    static final Map<String, Object> GENERATED = Map.of(
            "type", "object",
            "properties", Map.of(
                    "summary", Map.of("type", "string", "description", "What this change set implements, 1-3 sentences."),
                    "files", Map.of("type", "array", "items", FILE)),
            "required", List.of("summary", "files"),
            "additionalProperties", false);
}

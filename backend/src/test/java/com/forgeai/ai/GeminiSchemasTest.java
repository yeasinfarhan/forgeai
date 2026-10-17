package com.forgeai.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class GeminiSchemasTest {

    @Test
    @SuppressWarnings("unchecked")
    void convertsJsonSchemaToGeminiResponseSchema() {
        Map<String, Object> brief = Map.of(
                "type", "object",
                "properties", Map.of("title", Map.of("type", "string")),
                "required", List.of("title"),
                "additionalProperties", false);
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "decision", Map.of("type", "string", "enum", List.of("ask_questions", "write_brief")),
                        "questions", Map.of("type", "array", "items", Map.of("type", "string")),
                        "brief", Map.of(
                                "description", "The brief, or null.",
                                "anyOf", List.of(brief, Map.of("type", "null")))),
                "required", List.of("decision", "questions", "brief"),
                "additionalProperties", false);

        Map<String, Object> converted = GeminiSchemas.toResponseSchema(schema);

        assertThat(converted).containsEntry("type", "OBJECT").doesNotContainKey("additionalProperties");
        assertThat(converted.get("required")).isEqualTo(List.of("decision", "questions", "brief"));

        Map<String, Map<String, Object>> properties = (Map<String, Map<String, Object>>) converted.get("properties");
        assertThat(properties.get("decision"))
                .containsEntry("type", "STRING")
                .containsEntry("enum", List.of("ask_questions", "write_brief"));
        assertThat((Map<String, Object>) properties.get("questions").get("items")).containsEntry("type", "STRING");

        // anyOf [object, null] becomes a nullable object
        assertThat(properties.get("brief"))
                .containsEntry("type", "OBJECT")
                .containsEntry("nullable", true)
                .containsEntry("description", "The brief, or null.")
                .doesNotContainKey("anyOf")
                .doesNotContainKey("additionalProperties");
    }
}

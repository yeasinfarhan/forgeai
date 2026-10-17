package com.forgeai.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts the JSON Schemas ForgeAI writes (the subset Claude's structured outputs accept) into
 * Gemini's {@code responseSchema} format, an OpenAPI-style subset:
 *
 * <ul>
 *   <li>type names are upper-case ({@code OBJECT}, {@code STRING} …);</li>
 *   <li>{@code anyOf: [X, {type: null}]} becomes {@code X} with {@code nullable: true};</li>
 *   <li>{@code additionalProperties} is dropped (Gemini only returns declared properties anyway).</li>
 * </ul>
 */
final class GeminiSchemas {

    private GeminiSchemas() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> toResponseSchema(Map<String, ?> schema) {
        if (schema.get("anyOf") instanceof List<?> options) {
            return convertAnyOf(schema, (List<Map<String, ?>>) options);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        schema.forEach((key, value) -> {
            switch (key) {
                case "type" -> result.put("type", value.toString().toUpperCase(Locale.ROOT));
                case "properties" -> {
                    Map<String, Object> properties = new LinkedHashMap<>();
                    ((Map<String, Map<String, ?>>) value).forEach((name, propertySchema) ->
                            properties.put(name, toResponseSchema(propertySchema)));
                    result.put("properties", properties);
                }
                case "items" -> result.put("items", toResponseSchema((Map<String, ?>) value));
                case "additionalProperties" -> {
                    // Not part of Gemini's responseSchema.
                }
                default -> result.put(key, value); // description, enum, required, format, nullable …
            }
        });
        return result;
    }

    private static Map<String, Object> convertAnyOf(Map<String, ?> schema, List<Map<String, ?>> options) {
        List<Map<String, ?>> nonNull = options.stream().filter(option -> !"null".equals(option.get("type"))).toList();
        boolean nullable = nonNull.size() < options.size();

        Map<String, Object> result;
        if (nonNull.size() == 1) {
            result = toResponseSchema(nonNull.getFirst());
        } else {
            List<Map<String, Object>> converted = new ArrayList<>();
            nonNull.forEach(option -> converted.add(toResponseSchema(option)));
            result = new LinkedHashMap<>();
            result.put("anyOf", converted);
        }
        if (nullable) {
            result.put("nullable", true);
        }
        if (schema.get("description") != null) {
            result.putIfAbsent("description", schema.get("description"));
        }
        return result;
    }
}

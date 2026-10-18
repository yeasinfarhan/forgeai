package com.forgeai.design.database;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** JSON schema for a generated database design (structured outputs; converted for Gemini automatically). */
final class DatabaseSchemas {

    private DatabaseSchemas() {
    }

    private static Map<String, Object> nullable(Map<String, Object> schema) {
        return Map.of("anyOf", List.of(schema, Map.of("type", "null")));
    }

    private static final Map<String, Object> FOREIGN_KEY = Map.of(
            "type", "object",
            "properties", Map.of(
                    "entity", Map.of("type", "string", "description", "Referenced entity (table) name."),
                    "column", Map.of("type", "string", "description", "Referenced column, normally its primary key."),
                    "onDelete", Map.of("type", "string", "enum", Arrays.stream(OnDelete.values()).map(Enum::name).toList())),
            "required", List.of("entity", "column", "onDelete"),
            "additionalProperties", false);

    private static final Map<String, Object> COLUMN = Map.of(
            "type", "object",
            "properties", Map.ofEntries(
                    Map.entry("name", Map.of("type", "string", "description", "snake_case column name.")),
                    Map.entry("type", Map.of("type", "string", "enum", Arrays.stream(ColumnType.values()).map(Enum::name).toList())),
                    Map.entry("length", nullable(Map.of("type", "integer", "description", "Characters; required for VARCHAR."))),
                    Map.entry("precision", nullable(Map.of("type", "integer", "description", "Total digits; required for DECIMAL."))),
                    Map.entry("scale", nullable(Map.of("type", "integer", "description", "Digits after the point, for DECIMAL."))),
                    Map.entry("nullable", Map.of("type", "boolean")),
                    Map.entry("primaryKey", Map.of("type", "boolean")),
                    Map.entry("unique", Map.of("type", "boolean")),
                    Map.entry("defaultValue", nullable(Map.of("type", "string",
                            "description", "UUID: \"random\"; TIMESTAMP: \"now\"; DATE: \"today\"; BOOLEAN: \"true\"/\"false\"; "
                                    + "numbers as digits; text as plain text; JSON: \"{}\" or \"[]\"."))),
                    Map.entry("references", nullable(FOREIGN_KEY)),
                    Map.entry("allowedValues", Map.of("type", "array",
                            "description", "Fixed set of values for a status-like text column; empty otherwise.",
                            "items", Map.of("type", "string"))),
                    Map.entry("description", Map.of("type", "string"))),
            "required", List.of("name", "type", "length", "precision", "scale", "nullable", "primaryKey", "unique",
                    "defaultValue", "references", "allowedValues", "description"),
            "additionalProperties", false);

    private static final Map<String, Object> INDEX = Map.of(
            "type", "object",
            "properties", Map.of(
                    "columns", Map.of("type", "array", "items", Map.of("type", "string")),
                    "unique", Map.of("type", "boolean")),
            "required", List.of("columns", "unique"),
            "additionalProperties", false);

    private static final Map<String, Object> ENTITY = Map.of(
            "type", "object",
            "properties", Map.of(
                    "name", Map.of("type", "string", "description", "snake_case plural table name, e.g. \"invoices\"."),
                    "description", Map.of("type", "string"),
                    "columns", Map.of("type", "array", "items", COLUMN),
                    "indexes", Map.of("type", "array",
                            "description", "Indexes for frequent lookups and foreign keys; primary keys and unique columns need none.",
                            "items", INDEX),
                    "requirements", Map.of("type", "array",
                            "description", "Codes of the requirements this entity serves, exactly as given.",
                            "items", Map.of("type", "string"))),
            "required", List.of("name", "description", "columns", "indexes", "requirements"),
            "additionalProperties", false);

    static final Map<String, Object> GENERATED = Map.of(
            "type", "object",
            "properties", Map.of(
                    "summary", Map.of("type", "string", "description", "How the data is organised and the conventions used."),
                    "entities", Map.of("type", "array", "items", ENTITY)),
            "required", List.of("summary", "entities"),
            "additionalProperties", false);
}

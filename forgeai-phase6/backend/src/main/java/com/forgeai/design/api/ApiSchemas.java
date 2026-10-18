package com.forgeai.design.api;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** JSON schema for a generated API design (structured outputs; converted for Gemini automatically). */
final class ApiSchemas {

    private ApiSchemas() {
    }

    private static Map<String, Object> nullableString(String description) {
        return Map.of("anyOf", List.of(Map.of("type", "string", "description", description), Map.of("type", "null")));
    }

    private static final List<String> FIELD_TYPES = Arrays.stream(FieldType.values()).map(Enum::name).toList();

    private static final Map<String, Object> ERROR = Map.of(
            "type", "object",
            "properties", Map.of(
                    "status", Map.of("type", "integer", "description", "HTTP status, 400-599."),
                    "code", Map.of("type", "string", "description", "snake_case code, e.g. \"not_found\"."),
                    "description", Map.of("type", "string", "description", "When the API returns it.")),
            "required", List.of("status", "code", "description"),
            "additionalProperties", false);

    private static final Map<String, Object> FIELD = Map.of(
            "type", "object",
            "properties", Map.of(
                    "name", Map.of("type", "string", "description", "camelCase field name."),
                    "type", Map.of("type", "string", "enum", FIELD_TYPES),
                    "ref", nullableString("Schema name when type is OBJECT; null otherwise."),
                    "array", Map.of("type", "boolean"),
                    "required", Map.of("type", "boolean"),
                    "description", Map.of("type", "string")),
            "required", List.of("name", "type", "ref", "array", "required", "description"),
            "additionalProperties", false);

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "name", Map.of("type", "string", "description", "PascalCase, e.g. \"InvoiceResponse\", \"CreateInvoiceRequest\"."),
                    "description", Map.of("type", "string"),
                    "entity", nullableString("Database entity (table name) this schema represents, if any."),
                    "fields", Map.of("type", "array", "items", FIELD)),
            "required", List.of("name", "description", "entity", "fields"),
            "additionalProperties", false);

    private static final Map<String, Object> PARAMETER = Map.of(
            "type", "object",
            "properties", Map.of(
                    "name", Map.of("type", "string", "description", "camelCase."),
                    "type", Map.of("type", "string", "enum", FIELD_TYPES.stream().filter(t -> !t.equals("OBJECT")).toList()),
                    "required", Map.of("type", "boolean"),
                    "description", Map.of("type", "string")),
            "required", List.of("name", "type", "required", "description"),
            "additionalProperties", false);

    private static final Map<String, Object> ENDPOINT = Map.of(
            "type", "object",
            "properties", Map.ofEntries(
                    Map.entry("method", Map.of("type", "string", "enum", Arrays.stream(HttpMethod.values()).map(Enum::name).toList())),
                    Map.entry("path", Map.of("type", "string",
                            "description", "Relative to the base path, with {name} placeholders, e.g. \"/invoices/{invoiceId}\".")),
                    Map.entry("summary", Map.of("type", "string")),
                    Map.entry("description", Map.of("type", "string")),
                    Map.entry("authenticated", Map.of("type", "boolean")),
                    Map.entry("entity", nullableString("Database entity the endpoint works on, if any.")),
                    Map.entry("requestSchema", nullableString("Request body schema name; null for GET and DELETE.")),
                    Map.entry("responseSchema", nullableString("Response body schema name; null for 204.")),
                    Map.entry("responseList", Map.of("type", "boolean", "description", "The response is a list of responseSchema.")),
                    Map.entry("successStatus", Map.of("type", "integer", "description", "200, 201 or 204.")),
                    Map.entry("pathParameters", Map.of("type", "array", "items", PARAMETER)),
                    Map.entry("queryParameters", Map.of("type", "array", "items", PARAMETER)),
                    Map.entry("errorStatuses", Map.of("type", "array",
                            "description", "Error statuses it may return; each must be in the error model.",
                            "items", Map.of("type", "integer"))),
                    Map.entry("requirements", Map.of("type", "array",
                            "description", "Codes of the requirements this endpoint serves, exactly as given.",
                            "items", Map.of("type", "string")))),
            "required", List.of("method", "path", "summary", "description", "authenticated", "entity", "requestSchema",
                    "responseSchema", "responseList", "successStatus", "pathParameters", "queryParameters", "errorStatuses",
                    "requirements"),
            "additionalProperties", false);

    static final Map<String, Object> GENERATED = Map.of(
            "type", "object",
            "properties", Map.of(
                    "summary", Map.of("type", "string", "description", "What the API offers and its conventions."),
                    "basePath", Map.of("type", "string", "description", "e.g. \"/api\"."),
                    "authentication", Map.of("type", "string", "description", "How clients authenticate."),
                    "errorFormat", Map.of("type", "string", "description", "How errors are returned."),
                    "errors", Map.of("type", "array", "items", ERROR),
                    "schemas", Map.of("type", "array", "items", SCHEMA),
                    "endpoints", Map.of("type", "array", "items", ENDPOINT)),
            "required", List.of("summary", "basePath", "authentication", "errorFormat", "errors", "schemas", "endpoints"),
            "additionalProperties", false);
}

package com.forgeai.design.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.forgeai.design.api.ApiDesignContent.Endpoint;
import com.forgeai.design.api.ApiDesignContent.ErrorCase;
import com.forgeai.design.api.ApiDesignContent.Field;
import com.forgeai.design.api.ApiDesignContent.Parameter;
import com.forgeai.design.api.ApiDesignContent.Schema;

/**
 * Builds an OpenAPI 3.1 document from an {@link ApiDesignContent}. Error responses use RFC 9457
 * problem details ({@code application/problem+json}); endpoints marked authenticated require the
 * bearer scheme. The result is a plain map, serialised to JSON by the caller.
 */
public final class OpenApiExporter {

    static final String PROBLEM = "Problem";

    private OpenApiExporter() {
    }

    public static Map<String, Object> export(ApiDesignContent api, String title, String version) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("openapi", "3.1.0");
        document.put("info", map("title", title, "version", version, "description", api.summary()));
        document.put("servers", List.of(map("url", api.basePath())));

        Map<String, Object> paths = new LinkedHashMap<>();
        Set<String> operationIds = new HashSet<>();
        Map<Integer, ErrorCase> errors = new LinkedHashMap<>();
        api.errors().forEach(e -> errors.put(e.status(), e));
        for (Endpoint endpoint : api.endpoints()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) paths.computeIfAbsent(endpoint.path(), p -> new LinkedHashMap<>());
            item.put(endpoint.method().name().toLowerCase(Locale.ROOT), operation(endpoint, errors, operationIds));
        }
        document.put("paths", paths);

        Map<String, Object> schemas = new LinkedHashMap<>();
        for (Schema schema : api.schemas()) {
            schemas.put(schema.name(), schema(schema));
        }
        schemas.put(PROBLEM, problemSchema(api));
        document.put("components", map(
                "schemas", schemas,
                "securitySchemes", map("bearerAuth", map(
                        "type", "http", "scheme", "bearer", "description", api.authentication()))));
        return document;
    }

    private static Map<String, Object> operation(Endpoint endpoint, Map<Integer, ErrorCase> errors, Set<String> operationIds) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operationId", operationId(endpoint, operationIds));
        operation.put("summary", endpoint.summary());
        if (endpoint.description() != null) operation.put("description", endpoint.description());
        operation.put("tags", List.of(endpoint.entity() == null ? "general" : endpoint.entity()));
        operation.put("security", endpoint.authenticated() ? List.of(map("bearerAuth", List.of())) : List.of());

        List<Object> parameters = new ArrayList<>();
        for (Parameter parameter : endpoint.pathParameters()) parameters.add(parameter(parameter, "path"));
        for (Parameter parameter : endpoint.queryParameters()) parameters.add(parameter(parameter, "query"));
        if (!parameters.isEmpty()) operation.put("parameters", parameters);

        if (endpoint.requestSchema() != null) {
            operation.put("requestBody", map("required", true,
                    "content", map("application/json", map("schema", ref(endpoint.requestSchema())))));
        }

        Map<String, Object> responses = new LinkedHashMap<>();
        Map<String, Object> success = new LinkedHashMap<>();
        success.put("description", successDescription(endpoint.successStatus()));
        if (endpoint.responseSchema() != null) {
            Object schema = endpoint.responseList()
                    ? map("type", "array", "items", ref(endpoint.responseSchema()))
                    : ref(endpoint.responseSchema());
            success.put("content", map("application/json", map("schema", schema)));
        }
        responses.put(String.valueOf(endpoint.successStatus()), success);
        for (Integer status : endpoint.errorStatuses()) {
            ErrorCase error = errors.get(status);
            responses.put(String.valueOf(status), map(
                    "description", error == null ? "Error" : error.description(),
                    "content", map("application/problem+json", map("schema", ref(PROBLEM)))));
        }
        operation.put("responses", responses);
        return operation;
    }

    private static Map<String, Object> parameter(Parameter parameter, String in) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", parameter.name());
        result.put("in", in);
        result.put("required", "path".equals(in) || parameter.required());
        if (parameter.description() != null) result.put("description", parameter.description());
        result.put("schema", scalar(parameter.type()));
        return result;
    }

    private static Map<String, Object> schema(Schema schema) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "object");
        if (schema.description() != null) result.put("description", schema.description());
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Field field : schema.fields()) {
            Map<String, Object> property = new LinkedHashMap<>(field.type() == FieldType.OBJECT ? ref(field.ref()) : scalar(field.type()));
            if (field.array()) property = new LinkedHashMap<>(map("type", "array", "items", property));
            if (field.description() != null) property.put("description", field.description());
            properties.put(field.name(), property);
            if (field.required()) required.add(field.name());
        }
        result.put("properties", properties);
        if (!required.isEmpty()) result.put("required", required);
        return result;
    }

    /** RFC 9457 problem details; the design's error codes are listed as the extension member "code". */
    private static Map<String, Object> problemSchema(ApiDesignContent api) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("type", map("type", "string", "format", "uri"));
        properties.put("title", map("type", "string"));
        properties.put("status", map("type", "integer"));
        properties.put("detail", map("type", "string"));
        properties.put("instance", map("type", "string"));
        List<String> codes = api.errors().stream().map(ErrorCase::code).distinct().toList();
        if (!codes.isEmpty()) properties.put("code", map("type", "string", "enum", codes));
        return map("type", "object", "description", api.errorFormat(), "properties", properties);
    }

    static Map<String, Object> scalar(FieldType type) {
        return switch (type) {
            case STRING -> map("type", "string");
            case INTEGER -> map("type", "integer", "format", "int64");
            case NUMBER -> map("type", "number");
            case BOOLEAN -> map("type", "boolean");
            case UUID -> map("type", "string", "format", "uuid");
            case DATE -> map("type", "string", "format", "date");
            case DATE_TIME -> map("type", "string", "format", "date-time");
            case EMAIL -> map("type", "string", "format", "email");
            case OBJECT -> map("type", "object");
        };
    }

    private static Map<String, Object> ref(String schema) {
        return map("$ref", "#/components/schemas/" + schema);
    }

    /** e.g. GET /invoices/{invoiceId} → getInvoicesByInvoiceId; made unique if needed. */
    static String operationId(Endpoint endpoint, Set<String> used) {
        StringBuilder id = new StringBuilder(endpoint.method().name().toLowerCase(Locale.ROOT));
        for (String segment : endpoint.path().split("/")) {
            if (segment.isEmpty()) continue;
            boolean parameter = segment.startsWith("{") && segment.endsWith("}");
            String word = parameter ? segment.substring(1, segment.length() - 1) : segment;
            if (parameter) id.append("By");
            for (String part : word.split("[^A-Za-z0-9]+")) {
                if (!part.isEmpty()) id.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        String unique = id.toString();
        for (int i = 2; !used.add(unique); i++) unique = id + String.valueOf(i);
        return unique;
    }

    private static String successDescription(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 204 -> "No content";
            default -> "Success";
        };
    }

    /** LinkedHashMap from alternating keys and values, keeping order (Map.of does not). */
    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}

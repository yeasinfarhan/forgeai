package com.forgeai.design.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.forgeai.ai.AiClient;
import com.forgeai.ai.AiException;
import com.forgeai.ai.AiFailure;
import com.forgeai.ai.AiRequest;
import com.forgeai.ai.AiResult;
import com.forgeai.ai.PromptTemplates;
import com.forgeai.design.api.ApiDesignContent.Endpoint;
import com.forgeai.design.api.ApiDesignContent.ErrorCase;
import com.forgeai.design.api.ApiDesignContent.Field;
import com.forgeai.design.api.ApiDesignContent.Parameter;
import com.forgeai.design.api.ApiDesignContent.Schema;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asks the AI for an API design and repairs what comes back so it satisfies the same rules as a
 * person's edit: valid names, schemas and statuses that exist, path parameters that match the
 * path, entities that exist in the data model, and only codes of approved requirements.
 */
@Component
class ApiAssistant {

    private static final Logger log = LoggerFactory.getLogger(ApiAssistant.class);
    private static final Pattern SCHEMA_NAME = Pattern.compile(ApiDesignContent.SCHEMA_NAME);
    private static final Pattern FIELD_NAME = Pattern.compile(ApiDesignContent.FIELD_NAME);
    private static final Pattern PATH = Pattern.compile("^/[A-Za-z0-9/{}_.-]*$");

    private final AiClient ai;
    private final PromptTemplates prompts;
    private final JsonMapper jsonMapper;

    ApiAssistant(AiClient ai, PromptTemplates prompts, JsonMapper jsonMapper) {
        this.ai = ai;
        this.prompts = prompts;
        this.jsonMapper = jsonMapper;
    }

    record Generated(ApiDesignContent content, String model) {
    }

    /**
     * @param database       the approved data model, as prompt text
     * @param entities       its entity names, to keep only real ones
     */
    Generated generate(String projectName, String architecture, String database, String requirements,
            Set<String> entities, Set<String> approvedCodes) {
        String user = prompts.render("api-generate", Map.of(
                "projectName", projectName,
                "architecture", architecture,
                "database", database,
                "requirements", requirements));
        AiResult result = ai.complete(new AiRequest("api-design.generate", prompts.render("api-system", Map.of()), user, ApiSchemas.GENERATED));
        Payload payload;
        try {
            payload = jsonMapper.readValue(result.text(), Payload.class);
        } catch (JacksonException ex) {
            throw new AiException(AiFailure.BAD_RESPONSE, "The model returned JSON that does not match the API schema", ex);
        }
        ApiDesignContent content = repair(payload, entities, approvedCodes);
        List<String> problems = ApiRules.problems(content, approvedCodes);
        if (!problems.isEmpty()) {
            log.warn("Repaired API design still has problems: {}", problems);
            throw new AiException(AiFailure.BAD_RESPONSE, "The model returned an inconsistent API design");
        }
        return new Generated(content, result.model());
    }

    // --- Model output ------------------------------------------------------------

    record Payload(String summary, String basePath, String authentication, String errorFormat,
            List<ErrorItem> errors, List<SchemaItem> schemas, List<EndpointItem> endpoints) {
    }

    record ErrorItem(Integer status, String code, String description) {
    }

    record SchemaItem(String name, String description, String entity, List<FieldItem> fields) {
    }

    record FieldItem(String name, String type, String ref, Boolean array, Boolean required, String description) {
    }

    record ParameterItem(String name, String type, Boolean required, String description) {
    }

    record EndpointItem(String method, String path, String summary, String description, Boolean authenticated,
            String entity, String requestSchema, String responseSchema, Boolean responseList, Integer successStatus,
            List<ParameterItem> pathParameters, List<ParameterItem> queryParameters, List<Integer> errorStatuses,
            List<String> requirements) {
    }

    static ApiDesignContent repair(Payload payload, Set<String> entities, Set<String> approvedCodes) {
        if (isBlank(payload.summary()) || isBlank(payload.authentication()) || isBlank(payload.errorFormat())) {
            throw new AiException(AiFailure.BAD_RESPONSE, "The model returned an API design without summary or conventions");
        }
        String basePath = isBlank(payload.basePath()) ? "/api" : ApiRules.normalisePath(payload.basePath());
        if (!PATH.matcher(basePath).matches() || basePath.contains("{")) basePath = "/api";

        // Error model
        Map<Integer, ErrorCase> errors = new LinkedHashMap<>();
        for (ErrorItem e : nonNull(payload.errors())) {
            if (e == null || e.status() == null || e.status() < 400 || e.status() > 599 || isBlank(e.code()) || isBlank(e.description())) continue;
            if (errors.size() == 20) break;
            errors.putIfAbsent(e.status(), new ErrorCase(e.status(), truncate(e.code(), 60), truncate(e.description(), 500)));
        }

        // Schemas: names first, so object fields can only refer to schemas that survive
        Map<String, SchemaItem> schemaItems = new LinkedHashMap<>();
        for (SchemaItem s : nonNull(payload.schemas())) {
            if (s == null || schemaItems.size() == ApiDesignContent.MAX_SCHEMAS) continue;
            String name = schemaName(s.name());
            if (name != null) schemaItems.putIfAbsent(name, s);
        }
        List<Schema> schemas = new ArrayList<>();
        schemaItems.forEach((name, s) -> {
            Map<String, Field> fields = new LinkedHashMap<>();
            for (FieldItem f : nonNull(s.fields())) {
                if (f == null || fields.size() == 60) continue;
                String fieldName = fieldName(f.name());
                if (fieldName == null || fields.containsKey(fieldName)) continue;
                FieldType type = parse(FieldType.class, f.type(), FieldType.STRING);
                String ref = null;
                if (type == FieldType.OBJECT) {
                    ref = schemaName(f.ref());
                    if (ref == null || !schemaItems.containsKey(ref)) continue; // unknown nested schema: leave the field out
                }
                fields.put(fieldName, new Field(fieldName, type, ref, Boolean.TRUE.equals(f.array()),
                        Boolean.TRUE.equals(f.required()), truncate(f.description(), 500)));
            }
            if (!fields.isEmpty()) {
                schemas.add(new Schema(name, truncate(s.description(), 500), entity(s.entity(), entities), List.copyOf(fields.values())));
            }
        });
        // A schema dropped for having no fields may be referenced by another schema's field, whose
        // removal can empty that schema in turn: repeat until nothing changes.
        Set<String> schemaNames = new HashSet<>();
        int before;
        do {
            before = schemas.size();
            schemaNames.clear();
            schemas.forEach(s -> schemaNames.add(s.name()));
            schemas.replaceAll(s -> new Schema(s.name(), s.description(), s.entity(),
                    s.fields().stream().filter(f -> f.ref() == null || schemaNames.contains(f.ref())).toList()));
            schemas.removeIf(s -> s.fields().isEmpty());
        } while (schemas.size() != before);

        // Endpoints
        List<Endpoint> endpoints = new ArrayList<>();
        Set<String> routes = new HashSet<>();
        for (EndpointItem e : nonNull(payload.endpoints())) {
            if (e == null || endpoints.size() == ApiDesignContent.MAX_ENDPOINTS || isBlank(e.path()) || isBlank(e.summary())) continue;
            HttpMethod method = parse(HttpMethod.class, e.method(), null);
            String path = ApiRules.normalisePath(e.path());
            if (method == null || !PATH.matcher(path).matches() || path.length() > 200) continue;
            List<String> placeholders = ApiRules.placeholders(path);
            if (placeholders.stream().anyMatch(p -> !FIELD_NAME.matcher(p).matches())) continue;
            if (!routes.add(method + " " + path.replaceAll("\\{[^{}]*}", "{}"))) continue;

            Map<String, ParameterItem> declared = new LinkedHashMap<>();
            nonNull(e.pathParameters()).stream().filter(p -> p != null && p.name() != null)
                    .forEach(p -> declared.putIfAbsent(p.name().strip(), p));
            List<Parameter> pathParameters = placeholders.stream().distinct().map(name -> {
                ParameterItem p = declared.get(name);
                FieldType type = p == null ? (name.endsWith("Id") || name.equals("id") ? FieldType.UUID : FieldType.STRING)
                        : scalar(parse(FieldType.class, p.type(), FieldType.STRING));
                return new Parameter(name, type, true, p == null ? null : truncate(p.description(), 300));
            }).toList();

            Map<String, Parameter> query = new LinkedHashMap<>();
            for (ParameterItem p : nonNull(e.queryParameters())) {
                if (p == null || query.size() == 15) continue;
                String name = fieldName(p.name());
                if (name == null || query.containsKey(name) || placeholders.contains(name)) continue;
                query.put(name, new Parameter(name, scalar(parse(FieldType.class, p.type(), FieldType.STRING)),
                        Boolean.TRUE.equals(p.required()), truncate(p.description(), 300)));
            }

            int successStatus = e.successStatus() == null || e.successStatus() < 200 || e.successStatus() > 299
                    ? (method == HttpMethod.POST ? 201 : 200) : e.successStatus();
            String requestSchema = method == HttpMethod.GET || method == HttpMethod.DELETE ? null : known(schemaName(e.requestSchema()), schemaNames);
            String responseSchema = successStatus == 204 ? null : known(schemaName(e.responseSchema()), schemaNames);

            endpoints.add(new Endpoint(
                    method,
                    path,
                    truncate(e.summary(), 200),
                    truncate(e.description(), 2_000),
                    !Boolean.FALSE.equals(e.authenticated()),
                    entity(e.entity(), entities),
                    requestSchema,
                    responseSchema,
                    responseSchema != null && Boolean.TRUE.equals(e.responseList()),
                    successStatus,
                    pathParameters,
                    List.copyOf(query.values()),
                    nonNull(e.errorStatuses()).stream().filter(Objects::nonNull).filter(errors::containsKey).distinct().limit(10).toList(),
                    nonNull(e.requirements()).stream().filter(Objects::nonNull)
                            .map(code -> code.strip().toUpperCase(Locale.ROOT)).filter(approvedCodes::contains).distinct().limit(100).toList()));
        }
        if (endpoints.isEmpty()) {
            throw new AiException(AiFailure.BAD_RESPONSE, "The model returned an API design without usable endpoints");
        }

        return ApiRules.tidy(new ApiDesignContent(
                truncate(payload.summary(), 4_000),
                truncate(basePath, 100),
                truncate(payload.authentication(), 2_000),
                truncate(payload.errorFormat(), 2_000),
                List.copyOf(errors.values()),
                schemas,
                endpoints));
    }

    /** PascalCase schema name, or null. "invoice_response" → "InvoiceResponse". */
    static String schemaName(String raw) {
        if (raw == null) return null;
        StringBuilder name = new StringBuilder();
        for (String part : raw.strip().split("[^A-Za-z0-9]+")) {
            if (!part.isEmpty()) name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        String result = name.length() > 63 ? name.substring(0, 63) : name.toString();
        return SCHEMA_NAME.matcher(result).matches() ? result : null;
    }

    /** camelCase-ish field name, or null. "Due Date" → "dueDate". */
    static String fieldName(String raw) {
        if (raw == null) return null;
        String[] parts = raw.strip().split("[^A-Za-z0-9_]+");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (name.isEmpty()) name.append(Character.toLowerCase(part.charAt(0))).append(part.substring(1));
            else name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        String result = name.length() > 63 ? name.substring(0, 63) : name.toString();
        return FIELD_NAME.matcher(result).matches() ? result : null;
    }

    private static String entity(String raw, Set<String> entities) {
        if (raw == null) return null;
        String name = raw.strip().toLowerCase(Locale.ROOT);
        return entities.contains(name) ? name : null;
    }

    private static String known(String name, Set<String> names) {
        return name != null && names.contains(name) ? name : null;
    }

    private static FieldType scalar(FieldType type) {
        return type == FieldType.OBJECT ? FieldType.STRING : type;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String truncate(String text, int max) {
        if (text == null) return null;
        String stripped = text.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max).strip();
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}

package com.forgeai.design.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.forgeai.design.DesignDtos.Area;
import com.forgeai.design.DesignDtos.Finding;
import com.forgeai.design.api.ApiDesignContent.Endpoint;
import com.forgeai.design.api.ApiDesignContent.ErrorCase;
import com.forgeai.design.api.ApiDesignContent.Field;
import com.forgeai.design.api.ApiDesignContent.Parameter;
import com.forgeai.design.api.ApiDesignContent.Schema;
import com.forgeai.design.database.DatabaseDesignContent;

/**
 * Rules that span several parts of an {@link ApiDesignContent}.
 *
 * <ul>
 *   <li>{@link #problems}: contract mistakes that block saving (unknown schemas, path parameters
 *       that do not match the path, error statuses missing from the error model …).</li>
 *   <li>{@link #findings}: mismatches with the data model for the review; they do not block saving.</li>
 * </ul>
 */
public final class ApiRules {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]*)}");

    private ApiRules() {
    }

    /** Trims text, normalises paths, drops values that do not apply, sorts and de-duplicates lists. */
    public static ApiDesignContent tidy(ApiDesignContent content) {
        return new ApiDesignContent(
                content.summary().strip(),
                normalisePath(content.basePath()),
                content.authentication().strip(),
                content.errorFormat().strip(),
                content.errors().stream()
                        .map(e -> new ErrorCase(e.status(), e.code().strip(), e.description().strip()))
                        .sorted((a, b) -> Integer.compare(a.status(), b.status()))
                        .toList(),
                content.schemas().stream().map(s -> new Schema(
                        s.name().strip(),
                        blankToNull(s.description()),
                        blankToNull(s.entity()),
                        s.fields().stream().map(f -> new Field(
                                f.name().strip(),
                                f.type(),
                                f.type() == FieldType.OBJECT ? blankToNull(f.ref()) : null,
                                f.array(),
                                f.required(),
                                blankToNull(f.description()))).toList())).toList(),
                content.endpoints().stream().map(e -> {
                    String responseSchema = blankToNull(e.responseSchema());
                    return new Endpoint(
                            e.method(),
                            normalisePath(e.path()),
                            e.summary().strip(),
                            blankToNull(e.description()),
                            e.authenticated(),
                            blankToNull(e.entity()),
                            blankToNull(e.requestSchema()),
                            responseSchema,
                            responseSchema != null && e.responseList(),
                            e.successStatus(),
                            e.pathParameters().stream().map(p -> param(p, true)).toList(),
                            e.queryParameters().stream().map(p -> param(p, p.required())).toList(),
                            new TreeSet<>(e.errorStatuses()).stream().toList(),
                            distinctCodes(e.requirements()));
                }).toList());
    }

    private static Parameter param(Parameter p, boolean required) {
        return new Parameter(p.name().strip(), p.type(), required, blankToNull(p.description()));
    }

    /** Problems a person has to fix before the design can be saved; empty when it is consistent. */
    public static List<String> problems(ApiDesignContent content, Set<String> requirementCodes) {
        List<String> problems = new ArrayList<>();

        Set<Integer> errorStatuses = new HashSet<>();
        for (ErrorCase error : content.errors()) {
            if (!errorStatuses.add(error.status())) {
                problems.add("The error model lists status " + error.status() + " twice.");
            }
        }

        Set<String> schemas = new HashSet<>();
        for (Schema schema : content.schemas()) {
            if (!schemas.add(schema.name())) {
                problems.add("Two schemas are called " + schema.name() + ".");
            }
        }
        for (Schema schema : content.schemas()) {
            Set<String> fields = new HashSet<>();
            for (Field field : schema.fields()) {
                String where = schema.name() + "." + field.name();
                if (!fields.add(field.name())) {
                    problems.add(schema.name() + " has two fields called " + field.name() + ".");
                }
                if (field.type() == FieldType.OBJECT) {
                    if (field.ref() == null) {
                        problems.add(where + " is an object and needs the schema it refers to.");
                    } else if (!schemas.contains(field.ref())) {
                        problems.add(where + " refers to " + field.ref() + ", which is not a schema.");
                    }
                }
            }
        }

        Set<String> routes = new HashSet<>();
        for (Endpoint endpoint : content.endpoints()) {
            String where = endpoint.method() + " " + endpoint.path();
            if (!routes.add(endpoint.method() + " " + PLACEHOLDER.matcher(endpoint.path()).replaceAll("{}"))) {
                problems.add(where + " is defined twice.");
            }

            List<String> placeholders = placeholders(endpoint.path());
            Set<String> declared = endpoint.pathParameters().stream().map(Parameter::name).collect(Collectors.toSet());
            for (String name : placeholders) {
                if (!declared.contains(name)) {
                    problems.add(where + ": the path parameter {" + name + "} needs a definition.");
                }
            }
            for (String name : declared) {
                if (!placeholders.contains(name)) {
                    problems.add(where + ": the path parameter " + name + " does not appear in the path.");
                }
            }
            Set<String> queryNames = new HashSet<>();
            for (Parameter parameter : endpoint.queryParameters()) {
                if (!queryNames.add(parameter.name())) {
                    problems.add(where + " has two query parameters called " + parameter.name() + ".");
                }
            }
            for (Parameter parameter : concat(endpoint.pathParameters(), endpoint.queryParameters())) {
                if (parameter.type() == FieldType.OBJECT) {
                    problems.add(where + ": parameter " + parameter.name() + " cannot be an object.");
                }
            }

            if (endpoint.requestSchema() != null) {
                if (endpoint.method() == HttpMethod.GET || endpoint.method() == HttpMethod.DELETE) {
                    problems.add(where + ": " + endpoint.method() + " requests have no body.");
                } else if (!schemas.contains(endpoint.requestSchema())) {
                    problems.add(where + " uses " + endpoint.requestSchema() + " as request body, which is not a schema.");
                }
            }
            if (endpoint.responseSchema() != null) {
                if (endpoint.successStatus() == 204) {
                    problems.add(where + ": a 204 response has no body.");
                } else if (!schemas.contains(endpoint.responseSchema())) {
                    problems.add(where + " uses " + endpoint.responseSchema() + " as response body, which is not a schema.");
                }
            }
            for (Integer status : endpoint.errorStatuses()) {
                if (!errorStatuses.contains(status)) {
                    problems.add(where + " can return " + status + ", which is not in the error model.");
                }
            }
            for (String code : endpoint.requirements()) {
                if (!requirementCodes.contains(code)) {
                    problems.add(where + " refers to " + code + ", which is not a requirement of this project.");
                }
            }
        }
        return problems;
    }

    /**
     * Mismatches between the API and the data model: entities the API names that do not exist,
     * schema fields without a matching column, entities no endpoint works with, unused schemas.
     */
    public static List<Finding> findings(ApiDesignContent api, DatabaseDesignContent database) {
        List<Finding> findings = new ArrayList<>();
        Map<String, Set<String>> columns = new HashMap<>();
        if (database != null) {
            database.entities().forEach(e -> columns.put(e.name(),
                    e.columns().stream().map(DatabaseDesignContent.Column::name).collect(Collectors.toSet())));
        }

        Set<String> usedEntities = new HashSet<>();
        for (Schema schema : api.schemas()) {
            if (schema.entity() == null || database == null) continue;
            usedEntities.add(schema.entity());
            Set<String> entityColumns = columns.get(schema.entity());
            if (entityColumns == null) {
                findings.add(Finding.error(Area.API, "Schema " + schema.name() + " represents " + schema.entity()
                        + ", which is not an entity of the database design."));
                continue;
            }
            for (Field field : schema.fields()) {
                if (field.type() == FieldType.OBJECT) continue;
                String column = snakeCase(field.name());
                if (!entityColumns.contains(column) && !entityColumns.contains(column + "_id")) {
                    findings.add(Finding.warning(Area.API, schema.name() + "." + field.name() + " has no matching column in "
                            + schema.entity() + "."));
                }
            }
        }
        for (Endpoint endpoint : api.endpoints()) {
            if (endpoint.entity() == null || database == null) continue;
            usedEntities.add(endpoint.entity());
            if (!columns.containsKey(endpoint.entity())) {
                findings.add(Finding.error(Area.API, endpoint.method() + " " + endpoint.path() + " works on " + endpoint.entity()
                        + ", which is not an entity of the database design."));
            }
        }
        if (database != null) {
            for (DatabaseDesignContent.Entity entity : database.entities()) {
                if (!usedEntities.contains(entity.name())) {
                    findings.add(Finding.warning(Area.API, "No endpoint or schema works with the entity " + entity.name() + "."));
                }
            }
        }

        Set<String> usedSchemas = new HashSet<>();
        for (Endpoint endpoint : api.endpoints()) {
            if (endpoint.requestSchema() != null) usedSchemas.add(endpoint.requestSchema());
            if (endpoint.responseSchema() != null) usedSchemas.add(endpoint.responseSchema());
        }
        for (Schema schema : api.schemas()) {
            schema.fields().stream().filter(f -> f.ref() != null).forEach(f -> usedSchemas.add(f.ref()));
        }
        for (Schema schema : api.schemas()) {
            if (!usedSchemas.contains(schema.name())) {
                findings.add(Finding.warning(Area.API, "Schema " + schema.name() + " is not used by any endpoint."));
            }
        }
        return findings;
    }

    /** Requirement codes referenced by endpoints (upper-case). */
    public static Set<String> coveredRequirements(ApiDesignContent content) {
        Set<String> covered = new TreeSet<>();
        content.endpoints().forEach(e -> covered.addAll(e.requirements()));
        return covered;
    }

    static List<String> placeholders(String path) {
        List<String> names = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(path);
        while (matcher.find()) names.add(matcher.group(1));
        return names;
    }

    /** "/invoices/" → "/invoices"; "invoices" → "/invoices"; "/" stays "/". */
    static String normalisePath(String path) {
        String p = path.strip();
        if (!p.startsWith("/")) p = "/" + p;
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    /** dueDate → due_date. */
    static String snakeCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private static List<Parameter> concat(List<Parameter> a, List<Parameter> b) {
        List<Parameter> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static List<String> distinctCodes(List<String> codes) {
        Set<String> seen = new LinkedHashSet<>();
        for (String code : codes) {
            if (code != null && !code.isBlank()) seen.add(code.strip().toUpperCase(Locale.ROOT));
        }
        return List.copyOf(seen);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}

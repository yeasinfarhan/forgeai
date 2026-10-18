package com.forgeai.design.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The HTTP API of a project: conventions, the error model, the schemas (request and response
 * bodies) and the endpoints. Cross-references are checked by {@link ApiRules}; the OpenAPI export
 * is derived from this alone.
 *
 * @param summary        what the API offers and its conventions
 * @param basePath       prefix of every endpoint path, e.g. "/api"
 * @param authentication how clients authenticate
 * @param errorFormat    how errors are returned (body format)
 * @param errors         the error model: every error status endpoints may return, with its meaning
 */
public record ApiDesignContent(
        @NotBlank @Size(max = 4_000) String summary,
        @NotBlank @Size(max = 100) @Pattern(regexp = "^/[A-Za-z0-9/_.-]*$", message = "must start with / and contain only letters, digits, /, _, . and -") String basePath,
        @NotBlank @Size(max = 2_000) String authentication,
        @NotBlank @Size(max = 2_000) String errorFormat,
        @NotNull @Size(max = 20) List<@NotNull @Valid ErrorCase> errors,
        @NotNull @Size(max = ApiDesignContent.MAX_SCHEMAS) List<@NotNull @Valid Schema> schemas,
        @NotNull @Size(min = 1, max = ApiDesignContent.MAX_ENDPOINTS) List<@NotNull @Valid Endpoint> endpoints) {

    public static final int MAX_SCHEMAS = 60;
    public static final int MAX_ENDPOINTS = 100;
    public static final String SCHEMA_NAME = "^[A-Z][A-Za-z0-9]{0,62}$";
    public static final String FIELD_NAME = "^[a-z][A-Za-z0-9_]{0,62}$";
    private static final String SCHEMA_NAME_MESSAGE = "must be PascalCase letters and digits, e.g. InvoiceResponse";
    private static final String FIELD_NAME_MESSAGE = "must start with a lower-case letter and contain only letters, digits and _";

    /** @param code short machine-readable code, e.g. "not_found" */
    public record ErrorCase(
            @Min(400) @Max(599) int status,
            @NotBlank @Size(max = 60) String code,
            @NotBlank @Size(max = 500) String description) {
    }

    /** @param entity database entity this schema represents, if any (checked against the data model) */
    public record Schema(
            @NotBlank @Pattern(regexp = SCHEMA_NAME, message = SCHEMA_NAME_MESSAGE) String name,
            @Size(max = 500) String description,
            @Size(max = 63) String entity,
            @NotNull @Size(min = 1, max = 60) List<@NotNull @Valid Field> fields) {
    }

    /** @param ref schema name, for {@link FieldType#OBJECT} */
    public record Field(
            @NotBlank @Pattern(regexp = FIELD_NAME, message = FIELD_NAME_MESSAGE) String name,
            @NotNull FieldType type,
            @Size(max = 63) String ref,
            boolean array,
            boolean required,
            @Size(max = 500) String description) {
    }

    /** A path or query parameter. Path parameters are always required. */
    public record Parameter(
            @NotBlank @Pattern(regexp = FIELD_NAME, message = FIELD_NAME_MESSAGE) String name,
            @NotNull FieldType type,
            boolean required,
            @Size(max = 300) String description) {
    }

    /**
     * @param path           relative to the base path, with {name} placeholders, e.g. "/invoices/{invoiceId}"
     * @param entity         database entity the endpoint works on, if any
     * @param requestSchema  body schema name; none for GET and DELETE
     * @param responseSchema body schema name; none for 204
     * @param responseList   the response body is a list of {@code responseSchema}
     * @param errorStatuses  error statuses it may return; each must be in the error model
     * @param requirements   codes of the requirements this endpoint serves
     */
    public record Endpoint(
            @NotNull HttpMethod method,
            @NotBlank @Size(max = 200) @Pattern(regexp = "^/[A-Za-z0-9/{}_.-]*$", message = "must start with / and contain only letters, digits, /, {}, _, . and -") String path,
            @NotBlank @Size(max = 200) String summary,
            @Size(max = 2_000) String description,
            boolean authenticated,
            @Size(max = 63) String entity,
            @Size(max = 63) String requestSchema,
            @Size(max = 63) String responseSchema,
            boolean responseList,
            @Min(200) @Max(299) int successStatus,
            @NotNull @Size(max = 10) List<@NotNull @Valid Parameter> pathParameters,
            @NotNull @Size(max = 15) List<@NotNull @Valid Parameter> queryParameters,
            @NotNull @Size(max = 10) List<@NotNull @Min(400) @Max(599) Integer> errorStatuses,
            @NotNull @Size(max = 100) List<@NotBlank @Size(max = 20) String> requirements) {
    }
}

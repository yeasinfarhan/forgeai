package com.forgeai.design.database;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The data model of a project: entities (tables), their columns, keys and indexes. Relationships
 * are the foreign keys, so the ER diagram and the SQL export are derived from this alone.
 * Cross-references are checked by {@link DatabaseRules}.
 *
 * @param summary how the data is organised, conventions used
 */
public record DatabaseDesignContent(
        @NotBlank @Size(max = 4_000) String summary,
        @NotNull @Size(min = 1, max = DatabaseDesignContent.MAX_ENTITIES) List<@NotNull @Valid Entity> entities) {

    public static final int MAX_ENTITIES = 60;
    public static final int MAX_COLUMNS = 80;
    /** Lower-case snake_case, at most 63 characters (PostgreSQL's identifier limit). */
    public static final String IDENTIFIER = "^[a-z][a-z0-9_]{0,62}$";
    private static final String IDENTIFIER_MESSAGE = "must be lower-case snake_case (letters, digits, _), starting with a letter";

    /** @param requirements codes of the requirements this entity serves (e.g. FR-001) */
    public record Entity(
            @NotBlank @Pattern(regexp = IDENTIFIER, message = IDENTIFIER_MESSAGE) String name,
            @Size(max = 1_000) String description,
            @NotNull @Size(min = 1, max = MAX_COLUMNS) List<@NotNull @Valid Column> columns,
            @NotNull @Size(max = 30) List<@NotNull @Valid Index> indexes,
            @NotNull @Size(max = 100) List<@NotBlank @Size(max = 20) String> requirements) {
    }

    /**
     * @param length        characters, for VARCHAR
     * @param precision     total digits, for DECIMAL
     * @param scale         digits after the point, for DECIMAL
     * @param defaultValue  see {@link SqlDefaults} for what each type accepts
     * @param references    foreign key, or null
     * @param allowedValues fixed set of values for a text column (becomes a CHECK constraint)
     */
    public record Column(
            @NotBlank @Pattern(regexp = IDENTIFIER, message = IDENTIFIER_MESSAGE) String name,
            @NotNull ColumnType type,
            @Min(1) @Max(10_485_760) Integer length,
            @Min(1) @Max(1_000) Integer precision,
            @Min(0) @Max(1_000) Integer scale,
            boolean nullable,
            boolean primaryKey,
            boolean unique,
            @Size(max = 200) String defaultValue,
            @Valid ForeignKey references,
            @NotNull @Size(max = 50) List<@NotBlank @Size(max = 100) String> allowedValues,
            @Size(max = 500) String description) {
    }

    public record ForeignKey(
            @NotBlank @Pattern(regexp = IDENTIFIER, message = IDENTIFIER_MESSAGE) String entity,
            @NotBlank @Pattern(regexp = IDENTIFIER, message = IDENTIFIER_MESSAGE) String column,
            @NotNull OnDelete onDelete) {
    }

    public record Index(
            @NotNull @Size(min = 1, max = 8) List<@NotBlank @Pattern(regexp = IDENTIFIER, message = IDENTIFIER_MESSAGE) String> columns,
            boolean unique) {
    }
}

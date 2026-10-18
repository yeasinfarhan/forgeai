package com.forgeai.design.database;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.forgeai.ai.AiClient;
import com.forgeai.ai.AiException;
import com.forgeai.ai.AiFailure;
import com.forgeai.ai.AiRequest;
import com.forgeai.ai.AiResult;
import com.forgeai.ai.PromptTemplates;
import com.forgeai.design.database.DatabaseDesignContent.Column;
import com.forgeai.design.database.DatabaseDesignContent.Entity;
import com.forgeai.design.database.DatabaseDesignContent.ForeignKey;
import com.forgeai.design.database.DatabaseDesignContent.Index;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asks the AI for a data model and repairs what comes back so it satisfies the same rules as a
 * person's edit: valid snake_case names, no duplicates, references to real tables and columns,
 * type parameters present, allowed defaults, and only codes of approved requirements.
 */
@Component
class DatabaseAssistant {

    private static final Logger log = LoggerFactory.getLogger(DatabaseAssistant.class);

    /** Used when the model leaves out the precision of a DECIMAL column. */
    static final int DEFAULT_PRECISION = 19;
    static final int DEFAULT_SCALE = 4;

    private final AiClient ai;
    private final PromptTemplates prompts;
    private final JsonMapper jsonMapper;

    DatabaseAssistant(AiClient ai, PromptTemplates prompts, JsonMapper jsonMapper) {
        this.ai = ai;
        this.prompts = prompts;
        this.jsonMapper = jsonMapper;
    }

    record Generated(DatabaseDesignContent content, String model) {
    }

    Generated generate(String projectName, String architecture, String requirements, Set<String> approvedCodes) {
        String user = prompts.render("database-generate", Map.of(
                "projectName", projectName,
                "architecture", architecture,
                "requirements", requirements));
        AiResult result = ai.complete(new AiRequest(
                "database-design.generate", prompts.render("database-system", Map.of()), user, DatabaseSchemas.GENERATED));
        Payload payload;
        try {
            payload = jsonMapper.readValue(result.text(), Payload.class);
        } catch (JacksonException ex) {
            throw new AiException(AiFailure.BAD_RESPONSE, "The model returned JSON that does not match the database schema", ex);
        }
        DatabaseDesignContent content = repair(payload, approvedCodes);
        List<String> problems = DatabaseRules.problems(content, approvedCodes);
        if (!problems.isEmpty()) {
            log.warn("Repaired database design still has problems: {}", problems);
            throw new AiException(AiFailure.BAD_RESPONSE, "The model returned an inconsistent database design");
        }
        return new Generated(content, result.model());
    }

    // --- Model output ------------------------------------------------------------

    record Payload(String summary, List<EntityItem> entities) {
    }

    record EntityItem(String name, String description, List<ColumnItem> columns, List<IndexItem> indexes, List<String> requirements) {
    }

    record ColumnItem(
            String name,
            String type,
            Integer length,
            Integer precision,
            Integer scale,
            Boolean nullable,
            Boolean primaryKey,
            Boolean unique,
            String defaultValue,
            ForeignKeyItem references,
            List<String> allowedValues,
            String description) {
    }

    record ForeignKeyItem(String entity, String column, String onDelete) {
    }

    record IndexItem(List<String> columns, Boolean unique) {
    }

    static DatabaseDesignContent repair(Payload payload, Set<String> approvedCodes) {
        if (isBlank(payload.summary())) {
            throw new AiException(AiFailure.BAD_RESPONSE, "The model returned a database design without summary");
        }

        // Pass 1: entities and columns with clean names, so references can be checked in pass 2.
        Map<String, EntityItem> items = new LinkedHashMap<>();
        Map<String, Map<String, ColumnItem>> columnsByEntity = new LinkedHashMap<>();
        for (EntityItem item : nonNull(payload.entities())) {
            if (item == null || items.size() == DatabaseDesignContent.MAX_ENTITIES) continue;
            String name = identifier(item.name());
            if (name == null || items.containsKey(name)) continue;
            Map<String, ColumnItem> columns = new LinkedHashMap<>();
            for (ColumnItem column : nonNull(item.columns())) {
                if (column == null || columns.size() == DatabaseDesignContent.MAX_COLUMNS) continue;
                String columnName = identifier(column.name());
                if (columnName != null) columns.putIfAbsent(columnName, column);
            }
            if (columns.isEmpty()) continue;
            items.put(name, item);
            columnsByEntity.put(name, columns);
        }
        if (items.isEmpty()) {
            throw new AiException(AiFailure.BAD_RESPONSE, "The model returned a database design without entities");
        }

        List<Entity> entities = new ArrayList<>();
        items.forEach((name, item) -> {
            Map<String, ColumnItem> columnItems = columnsByEntity.get(name);
            List<Column> columns = new ArrayList<>();
            columnItems.forEach((columnName, c) -> columns.add(column(columnName, c, columnsByEntity)));
            List<Index> indexes = nonNull(item.indexes()).stream()
                    .filter(Objects::nonNull)
                    .limit(30)
                    .map(i -> new Index(nonNull(i.columns()).stream()
                            .map(DatabaseAssistant::identifier)
                            .filter(n -> n != null && columnItems.containsKey(n))
                            .distinct()
                            .limit(8)
                            .toList(), Boolean.TRUE.equals(i.unique())))
                    .filter(i -> !i.columns().isEmpty())
                    .toList();
            entities.add(new Entity(name, truncate(item.description(), 1_000), columns, indexes,
                    nonNull(item.requirements()).stream()
                            .filter(Objects::nonNull)
                            .map(code -> code.strip().toUpperCase(Locale.ROOT))
                            .filter(approvedCodes::contains)
                            .distinct()
                            .limit(100)
                            .toList()));
        });
        return DatabaseRules.tidy(new DatabaseDesignContent(truncate(payload.summary(), 4_000), entities));
    }

    private static Column column(String name, ColumnItem c, Map<String, Map<String, ColumnItem>> columnsByEntity) {
        ColumnType type = parse(ColumnType.class, c.type(), ColumnType.TEXT);
        Integer length = null;
        Integer precision = null;
        Integer scale = null;
        if (type == ColumnType.VARCHAR) {
            if (c.length() == null || c.length() < 1) {
                type = ColumnType.TEXT; // no length given: unbounded text is the honest choice
            } else {
                length = Math.min(c.length(), 10_485_760);
            }
        }
        if (type == ColumnType.DECIMAL) {
            precision = c.precision() == null || c.precision() < 1 ? DEFAULT_PRECISION : Math.min(c.precision(), 1_000);
            scale = c.precision() == null ? DEFAULT_SCALE : c.scale() == null ? null : Math.max(0, Math.min(c.scale(), precision));
        }
        boolean primaryKey = Boolean.TRUE.equals(c.primaryKey());
        boolean nullable = !primaryKey && !Boolean.FALSE.equals(c.nullable());

        List<String> allowedValues = type == ColumnType.TEXT || type == ColumnType.VARCHAR
                ? nonNull(c.allowedValues()).stream().filter(v -> !isBlank(v)).map(v -> truncate(v, 100)).distinct().limit(50).toList()
                : List.of();

        String defaultValue = isBlank(c.defaultValue()) ? null : truncate(c.defaultValue(), 200);
        if (defaultValue != null && (SqlDefaults.render(type, defaultValue).isEmpty()
                || (!allowedValues.isEmpty() && !allowedValues.contains(defaultValue)))) {
            defaultValue = null;
        }

        ForeignKey references = null;
        ForeignKeyItem fk = c.references();
        if (fk != null) {
            String entity = identifier(fk.entity());
            String column = identifier(fk.column());
            if (entity != null && column != null && columnsByEntity.containsKey(entity)
                    && columnsByEntity.get(entity).containsKey(column)) {
                OnDelete onDelete = parse(OnDelete.class, fk.onDelete(), OnDelete.RESTRICT);
                if (onDelete == OnDelete.SET_NULL && !nullable) onDelete = OnDelete.RESTRICT;
                references = new ForeignKey(entity, column, onDelete);
            }
        }
        return new Column(name, type, length, precision, scale, nullable, primaryKey, Boolean.TRUE.equals(c.unique()),
                defaultValue, references, allowedValues, truncate(c.description(), 500));
    }

    /** snake_case identifier of at most 63 characters, or null if nothing usable is left. */
    static String identifier(String raw) {
        if (raw == null) return null;
        String name = raw.strip()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (name.isEmpty()) return null;
        if (!Character.isLetter(name.charAt(0))) name = "t_" + name;
        return name.length() > 63 ? name.substring(0, 63) : name;
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

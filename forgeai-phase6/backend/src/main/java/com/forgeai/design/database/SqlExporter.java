package com.forgeai.design.database;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.forgeai.common.util.Fingerprints;
import com.forgeai.design.database.DatabaseDesignContent.Column;
import com.forgeai.design.database.DatabaseDesignContent.Entity;
import com.forgeai.design.database.DatabaseDesignContent.Index;

/**
 * Writes a {@link DatabaseDesignContent} as a PostgreSQL schema script: tables with their primary
 * keys, unique and check constraints first, then foreign keys (so the order of tables and circular
 * references do not matter), then indexes. Identifiers are validated snake_case and quoted anyway;
 * defaults come from the closed set in {@link SqlDefaults}.
 */
public final class SqlExporter {

    private static final int MAX_IDENTIFIER = 63;

    private SqlExporter() {
    }

    /**
     * @param header comment lines placed at the top (project, revision, status)
     */
    public static String export(DatabaseDesignContent content, List<String> header) {
        StringBuilder sql = new StringBuilder();
        header.forEach(line -> sql.append("-- ").append(line.replace('\n', ' ')).append('\n'));
        sql.append('\n');

        Set<String> names = new HashSet<>();
        for (Entity entity : content.entities()) {
            createTable(sql, entity, names);
        }

        List<String> foreignKeys = new ArrayList<>();
        for (Entity entity : content.entities()) {
            for (Column column : entity.columns()) {
                if (column.references() == null) continue;
                foreignKeys.add("ALTER TABLE " + quote(entity.name()) + " ADD CONSTRAINT "
                        + quote(constraintName("fk", names, entity.name(), column.name()))
                        + " FOREIGN KEY (" + quote(column.name()) + ") REFERENCES " + quote(column.references().entity())
                        + " (" + quote(column.references().column()) + ") ON DELETE "
                        + column.references().onDelete().name().replace('_', ' ') + ";");
            }
        }
        if (!foreignKeys.isEmpty()) {
            sql.append("-- Foreign keys\n");
            foreignKeys.forEach(line -> sql.append(line).append('\n'));
            sql.append('\n');
        }

        List<String> indexes = new ArrayList<>();
        for (Entity entity : content.entities()) {
            for (Index index : entity.indexes()) {
                List<String> parts = new ArrayList<>();
                parts.add(entity.name());
                parts.addAll(index.columns());
                indexes.add("CREATE " + (index.unique() ? "UNIQUE " : "") + "INDEX "
                        + quote(constraintName(index.unique() ? "ux" : "ix", names, parts.toArray(String[]::new)))
                        + " ON " + quote(entity.name()) + " (" + quoteAll(index.columns()) + ");");
            }
        }
        if (!indexes.isEmpty()) {
            sql.append("-- Indexes\n");
            indexes.forEach(line -> sql.append(line).append('\n'));
        }
        return sql.toString().stripTrailing() + "\n";
    }

    private static void createTable(StringBuilder sql, Entity entity, Set<String> names) {
        List<String> lines = new ArrayList<>();
        for (Column column : entity.columns()) {
            StringBuilder line = new StringBuilder(quote(column.name())).append(' ').append(sqlType(column));
            if (!column.nullable()) line.append(" NOT NULL");
            if (column.defaultValue() != null) {
                SqlDefaults.render(column.type(), column.defaultValue())
                        .ifPresent(expression -> line.append(" DEFAULT ").append(expression));
            }
            lines.add(line.toString());
        }
        List<String> keys = entity.columns().stream().filter(Column::primaryKey).map(Column::name).toList();
        if (!keys.isEmpty()) {
            lines.add("CONSTRAINT " + quote(constraintName("pk", names, entity.name())) + " PRIMARY KEY (" + quoteAll(keys) + ")");
        }
        for (Column column : entity.columns()) {
            if (column.unique()) {
                lines.add("CONSTRAINT " + quote(constraintName("uk", names, entity.name(), column.name()))
                        + " UNIQUE (" + quote(column.name()) + ")");
            }
            if (!column.allowedValues().isEmpty()) {
                lines.add("CONSTRAINT " + quote(constraintName("ck", names, entity.name(), column.name()))
                        + " CHECK (" + quote(column.name()) + " IN ("
                        + column.allowedValues().stream().map(SqlDefaults::literal).collect(Collectors.joining(", ")) + "))");
            }
        }

        sql.append("CREATE TABLE ").append(quote(entity.name())).append(" (\n");
        sql.append(lines.stream().map(line -> "    " + line).collect(Collectors.joining(",\n")));
        sql.append("\n);\n");
        if (entity.description() != null) {
            sql.append("COMMENT ON TABLE ").append(quote(entity.name())).append(" IS ")
                    .append(SqlDefaults.literal(entity.description())).append(";\n");
        }
        for (Column column : entity.columns()) {
            if (column.description() != null) {
                sql.append("COMMENT ON COLUMN ").append(quote(entity.name())).append('.').append(quote(column.name()))
                        .append(" IS ").append(SqlDefaults.literal(column.description())).append(";\n");
            }
        }
        sql.append('\n');
    }

    static String sqlType(Column column) {
        return switch (column.type()) {
            case UUID -> "UUID";
            case TEXT -> "TEXT";
            case VARCHAR -> "VARCHAR(" + column.length() + ")";
            case INTEGER -> "INTEGER";
            case BIGINT -> "BIGINT";
            case DECIMAL -> "NUMERIC(" + column.precision() + (column.scale() == null ? "" : ", " + column.scale()) + ")";
            case BOOLEAN -> "BOOLEAN";
            case DATE -> "DATE";
            case TIMESTAMP -> "TIMESTAMPTZ";
            case JSON -> "JSONB";
        };
    }

    /**
     * {@code prefix_part1_part2}, unique within the script and at most 63 characters; longer names
     * are shortened and given a short hash so they stay unique.
     */
    static String constraintName(String prefix, Set<String> used, String... parts) {
        String name = prefix + "_" + String.join("_", parts);
        if (name.length() > MAX_IDENTIFIER) {
            name = name.substring(0, MAX_IDENTIFIER - 9) + "_" + Fingerprints.sha256(name).substring(0, 8);
        }
        String unique = name;
        for (int i = 2; !used.add(unique); i++) {
            String suffix = "_" + i;
            unique = name.substring(0, Math.min(name.length(), MAX_IDENTIFIER - suffix.length())) + suffix;
        }
        return unique;
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static String quoteAll(List<String> identifiers) {
        return identifiers.stream().map(SqlExporter::quote).collect(Collectors.joining(", "));
    }
}

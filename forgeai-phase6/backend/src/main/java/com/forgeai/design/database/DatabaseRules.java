package com.forgeai.design.database;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.forgeai.design.DesignDtos.Area;
import com.forgeai.design.DesignDtos.Finding;
import com.forgeai.design.database.DatabaseDesignContent.Column;
import com.forgeai.design.database.DatabaseDesignContent.Entity;
import com.forgeai.design.database.DatabaseDesignContent.ForeignKey;
import com.forgeai.design.database.DatabaseDesignContent.Index;

/**
 * Rules that span several parts of a {@link DatabaseDesignContent}.
 *
 * <ul>
 *   <li>{@link #problems}: structural mistakes that block saving (unknown references, duplicates,
 *       missing type parameters, invalid defaults).</li>
 *   <li>{@link #findings}: design issues the review should look at (no primary key, foreign key
 *       to a non-key column or of another type). They do not block saving.</li>
 * </ul>
 */
public final class DatabaseRules {

    private DatabaseRules() {
    }

    /** Trims text, removes duplicates and type parameters that do not apply, upper-cases codes. */
    public static DatabaseDesignContent tidy(DatabaseDesignContent content) {
        return new DatabaseDesignContent(
                content.summary().strip(),
                content.entities().stream().map(e -> new Entity(
                        e.name().strip(),
                        blankToNull(e.description()),
                        e.columns().stream().map(DatabaseRules::tidy).toList(),
                        e.indexes().stream().map(i -> new Index(distinct(i.columns(), false), i.unique())).toList(),
                        distinct(e.requirements(), true))).toList());
    }

    private static Column tidy(Column c) {
        boolean varchar = c.type() == ColumnType.VARCHAR;
        boolean decimal = c.type() == ColumnType.DECIMAL;
        boolean text = varchar || c.type() == ColumnType.TEXT;
        ForeignKey references = c.references() == null ? null
                : new ForeignKey(c.references().entity().strip(), c.references().column().strip(), c.references().onDelete());
        return new Column(
                c.name().strip(),
                c.type(),
                varchar ? c.length() : null,
                decimal ? c.precision() : null,
                decimal ? c.scale() : null,
                !c.primaryKey() && c.nullable(), // primary keys are never null
                c.primaryKey(),
                c.unique() && !c.primaryKey(),
                blankToNull(c.defaultValue()),
                references,
                text ? distinct(c.allowedValues(), false) : List.of(),
                blankToNull(c.description()));
    }

    /** Problems a person has to fix before the design can be saved; empty when it is consistent. */
    public static List<String> problems(DatabaseDesignContent content, Set<String> requirementCodes) {
        List<String> problems = new ArrayList<>();
        Map<String, Entity> entities = byName(content.entities(), Entity::name, n -> "Two entities are called " + n + ".", problems);

        for (Entity entity : content.entities()) {
            Map<String, Column> columns = byName(entity.columns(), Column::name,
                    n -> "Two columns of " + entity.name() + " are called " + n + ".", problems);
            for (Column column : entity.columns()) {
                String where = entity.name() + "." + column.name();
                if (column.type() == ColumnType.VARCHAR && column.length() == null) {
                    problems.add(where + " is VARCHAR and needs a length.");
                }
                if (column.type() == ColumnType.DECIMAL) {
                    if (column.precision() == null) {
                        problems.add(where + " is DECIMAL and needs a precision.");
                    } else if (column.scale() != null && column.scale() > column.precision()) {
                        problems.add(where + ": the scale cannot be larger than the precision.");
                    }
                }
                if (column.defaultValue() != null) {
                    if (SqlDefaults.render(column.type(), column.defaultValue()).isEmpty()) {
                        problems.add(where + ": the default for " + column.type() + " must be "
                                + SqlDefaults.allowed(column.type()) + ".");
                    } else if (!column.allowedValues().isEmpty() && !column.allowedValues().contains(column.defaultValue())) {
                        problems.add(where + ": the default must be one of the allowed values.");
                    }
                }
                ForeignKey fk = column.references();
                if (fk != null) {
                    Entity target = entities.get(fk.entity());
                    if (target == null) {
                        problems.add(where + " references " + fk.entity() + ", which is not an entity.");
                    } else if (target.columns().stream().noneMatch(c -> c.name().equals(fk.column()))) {
                        problems.add(where + " references " + fk.entity() + "." + fk.column() + ", which is not a column.");
                    }
                    if (fk.onDelete() == OnDelete.SET_NULL && !column.nullable()) {
                        problems.add(where + ": ON DELETE SET NULL needs a nullable column.");
                    }
                }
            }
            for (Index index : entity.indexes()) {
                for (String name : index.columns()) {
                    if (!columns.containsKey(name)) {
                        problems.add("An index on " + entity.name() + " uses " + name + ", which is not a column.");
                    }
                }
            }
            for (String code : entity.requirements()) {
                if (!requirementCodes.contains(code)) {
                    problems.add(entity.name() + " refers to " + code + ", which is not a requirement of this project.");
                }
            }
        }
        return problems;
    }

    /** Design issues for the review; they do not block saving. */
    public static List<Finding> findings(DatabaseDesignContent content) {
        List<Finding> findings = new ArrayList<>();
        Map<String, Entity> entities = content.entities().stream()
                .collect(Collectors.toMap(Entity::name, Function.identity(), (a, b) -> a));
        for (Entity entity : content.entities()) {
            long keys = entity.columns().stream().filter(Column::primaryKey).count();
            if (keys == 0) {
                findings.add(Finding.error(Area.DATABASE, entity.name() + " has no primary key."));
            }
            for (Column column : entity.columns()) {
                ForeignKey fk = column.references();
                if (fk == null) continue;
                Entity target = entities.get(fk.entity());
                if (target == null) continue;
                Optional<Column> referenced = target.columns().stream().filter(c -> c.name().equals(fk.column())).findFirst();
                if (referenced.isEmpty()) continue;
                String where = entity.name() + "." + column.name();
                if (referenced.get().type() != column.type()) {
                    findings.add(Finding.error(Area.DATABASE, where + " is " + column.type() + " but references " + fk.entity() + "."
                            + fk.column() + ", which is " + referenced.get().type() + "."));
                }
                if (!isKey(target, referenced.get())) {
                    findings.add(Finding.error(Area.DATABASE, where + " references " + fk.entity() + "." + fk.column()
                            + ", which is neither the primary key nor unique."));
                }
            }
        }
        return findings;
    }

    /** Requirement codes referenced by entities (upper-case). */
    public static Set<String> coveredRequirements(DatabaseDesignContent content) {
        Set<String> covered = new TreeSet<>();
        content.entities().forEach(e -> covered.addAll(e.requirements()));
        return covered;
    }

    /** A column other rows can point at: the single-column primary key, or unique. */
    static boolean isKey(Entity entity, Column column) {
        long keys = entity.columns().stream().filter(Column::primaryKey).count();
        if (column.primaryKey() && keys == 1) return true;
        if (column.unique()) return true;
        return entity.indexes().stream().anyMatch(i -> i.unique() && i.columns().equals(List.of(column.name())));
    }

    private static <T> Map<String, T> byName(
            List<T> items, Function<T, String> name, Function<String, String> duplicateMessage, List<String> problems) {
        Map<String, T> map = new LinkedHashMap<>();
        for (T item : items) {
            if (map.putIfAbsent(name.apply(item), item) != null) {
                problems.add(duplicateMessage.apply(name.apply(item)));
            }
        }
        return map;
    }

    private static List<String> distinct(List<String> values, boolean upperCase) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> keys = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            String cleaned = upperCase ? value.strip().toUpperCase(Locale.ROOT) : value.strip();
            if (keys.add(cleaned)) seen.add(cleaned);
        }
        return List.copyOf(seen);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}

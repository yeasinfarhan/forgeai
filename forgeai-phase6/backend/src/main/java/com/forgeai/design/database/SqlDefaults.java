package com.forgeai.design.database;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Default values a column may have, by type, and their PostgreSQL form. A closed set keeps the
 * SQL export valid and free of injected SQL:
 *
 * <ul>
 *   <li>UUID: {@code random} (a new random UUID)</li>
 *   <li>TEXT, VARCHAR: any text (written as a quoted literal)</li>
 *   <li>INTEGER, BIGINT: a whole number; DECIMAL: a number</li>
 *   <li>BOOLEAN: {@code true} or {@code false}</li>
 *   <li>DATE: {@code today}; TIMESTAMP: {@code now}</li>
 *   <li>JSON: {@code {}} or {@code []}</li>
 * </ul>
 */
public final class SqlDefaults {

    private static final Pattern WHOLE_NUMBER = Pattern.compile("-?\\d{1,18}");
    private static final Pattern NUMBER = Pattern.compile("-?\\d{1,30}(\\.\\d{1,30})?");

    private SqlDefaults() {
    }

    /** The SQL expression for the default, or empty if the value is not allowed for the type. */
    public static Optional<String> render(ColumnType type, String value) {
        String v = value.strip();
        String lower = v.toLowerCase(Locale.ROOT);
        return switch (type) {
            case UUID -> lower.equals("random") ? Optional.of("gen_random_uuid()") : Optional.empty();
            case TEXT, VARCHAR -> Optional.of(literal(v));
            case INTEGER, BIGINT -> WHOLE_NUMBER.matcher(v).matches() ? Optional.of(v) : Optional.empty();
            case DECIMAL -> NUMBER.matcher(v).matches() ? Optional.of(v) : Optional.empty();
            case BOOLEAN -> lower.equals("true") || lower.equals("false") ? Optional.of(lower.toUpperCase(Locale.ROOT)) : Optional.empty();
            case DATE -> lower.equals("today") ? Optional.of("CURRENT_DATE") : Optional.empty();
            case TIMESTAMP -> lower.equals("now") ? Optional.of("now()") : Optional.empty();
            case JSON -> v.equals("{}") || v.equals("[]") ? Optional.of("'" + v + "'::jsonb") : Optional.empty();
        };
    }

    /** What a person may type as default, for error messages. */
    static String allowed(ColumnType type) {
        return switch (type) {
            case UUID -> "\"random\"";
            case TEXT, VARCHAR -> "any text";
            case INTEGER, BIGINT -> "a whole number";
            case DECIMAL -> "a number";
            case BOOLEAN -> "\"true\" or \"false\"";
            case DATE -> "\"today\"";
            case TIMESTAMP -> "\"now\"";
            case JSON -> "\"{}\" or \"[]\"";
        };
    }

    /** A single-quoted SQL string literal. */
    static String literal(String text) {
        return "'" + text.replace("'", "''") + "'";
    }
}

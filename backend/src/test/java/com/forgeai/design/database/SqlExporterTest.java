package com.forgeai.design.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SqlExporterTest {

    @Test
    void constraintNamesStayWithinPostgresLimitsAndUnique() {
        Set<String> used = new HashSet<>();
        String table = "a_very_long_table_name_that_goes_on_and_on";
        String column = "and_a_column_name_that_is_long_too";

        String first = SqlExporter.constraintName("fk", used, table, column);
        String second = SqlExporter.constraintName("fk", used, table, column);

        assertThat(first).hasSizeLessThanOrEqualTo(63).startsWith("fk_a_very_long");
        assertThat(second).hasSizeLessThanOrEqualTo(63).isNotEqualTo(first).endsWith("_2");
        assertThat(SqlExporter.constraintName("pk", used, "users")).isEqualTo("pk_users");
    }

    @Test
    void defaultsAreLimitedToSafeValues() {
        assertThat(SqlDefaults.render(ColumnType.UUID, "random")).contains("gen_random_uuid()");
        assertThat(SqlDefaults.render(ColumnType.TEXT, "it's")).contains("'it''s'");
        assertThat(SqlDefaults.render(ColumnType.INTEGER, "1; DROP TABLE users")).isEmpty();
        assertThat(SqlDefaults.render(ColumnType.TIMESTAMP, "NOW")).contains("now()");
        assertThat(SqlDefaults.render(ColumnType.BOOLEAN, "yes")).isEmpty();
        assertThat(SqlDefaults.render(ColumnType.JSON, "[]")).contains("'[]'::jsonb");
    }
}

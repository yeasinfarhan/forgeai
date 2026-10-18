package com.forgeai.design.database;

/** Column types of the data model; the SQL export maps them to PostgreSQL types. */
public enum ColumnType {
    UUID,
    TEXT,
    /** Needs {@code length}. */
    VARCHAR,
    INTEGER,
    BIGINT,
    /** Needs {@code precision}; {@code scale} is optional. */
    DECIMAL,
    BOOLEAN,
    DATE,
    /** Point in time with time zone. */
    TIMESTAMP,
    JSON
}

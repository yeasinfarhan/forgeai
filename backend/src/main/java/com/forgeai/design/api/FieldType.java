package com.forgeai.design.api;

/** Field and parameter types of the API contracts; the OpenAPI export maps them to JSON Schema. */
public enum FieldType {
    STRING,
    INTEGER,
    NUMBER,
    BOOLEAN,
    UUID,
    DATE,
    DATE_TIME,
    EMAIL,
    /** Another schema of the API design, named by {@code ref}. Not allowed for parameters. */
    OBJECT
}

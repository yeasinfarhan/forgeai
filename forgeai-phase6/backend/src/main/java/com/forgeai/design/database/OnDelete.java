package com.forgeai.design.database;

/** What happens to referencing rows when the referenced row is deleted. */
public enum OnDelete {
    CASCADE,
    RESTRICT,
    SET_NULL
}

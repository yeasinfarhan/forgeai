package com.forgeai.design;

/** The design documents of phase 6. */
public enum DesignKind {
    DATABASE("database design"),
    API("API design");

    private final String label;

    DesignKind(String label) {
        this.label = label;
    }

    /** Lower-case name for messages, e.g. "database design". */
    public String label() {
        return label;
    }
}

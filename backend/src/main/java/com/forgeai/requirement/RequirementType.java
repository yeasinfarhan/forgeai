package com.forgeai.requirement;

public enum RequirementType {
    FUNCTIONAL("FR"),
    NON_FUNCTIONAL("NFR");

    private final String codePrefix;

    RequirementType(String codePrefix) {
        this.codePrefix = codePrefix;
    }

    public String code(int number) {
        return "%s-%03d".formatted(codePrefix, number);
    }
}

package com.forgeai.common.exception;

import java.util.List;

/**
 * The request is well-formed but its content breaks a rule that bean validation cannot express
 * (for example a reference to something that does not exist). Mapped to 400 with one message per problem.
 */
public class BadRequestException extends RuntimeException {

    private final List<String> problems;

    public BadRequestException(List<String> problems) {
        super(String.join(" ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}

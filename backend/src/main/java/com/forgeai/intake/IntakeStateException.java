package com.forgeai.intake;

/** The requested intake action does not fit the intake's current state. Mapped to HTTP 409. */
public class IntakeStateException extends RuntimeException {

    public IntakeStateException(String message) {
        super(message);
    }
}

package com.forgeai.auth;

/** Mapped to HTTP 409 by the global exception handler. */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("An account with this email already exists.");
    }
}

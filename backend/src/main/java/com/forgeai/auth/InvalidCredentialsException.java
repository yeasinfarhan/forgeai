package com.forgeai.auth;

/** Mapped to HTTP 401. The message never reveals whether the email or the password was wrong. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password.");
    }
}

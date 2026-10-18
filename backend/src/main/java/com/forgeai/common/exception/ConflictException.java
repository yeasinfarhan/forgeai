package com.forgeai.common.exception;

/**
 * The request is valid but conflicts with the resource's current state (wrong workflow step,
 * stale version …). Mapped to HTTP 409; the message is shown to users.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

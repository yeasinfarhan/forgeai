package com.forgeai.ai;

/**
 * An AI call failed. The {@link AiFailure} decides the HTTP response; the exception message is
 * for logs only and may contain technical detail that must not reach users.
 */
public class AiException extends RuntimeException {

    private final AiFailure failure;

    public AiException(AiFailure failure, String logMessage) {
        super(logMessage);
        this.failure = failure;
    }

    public AiException(AiFailure failure, String logMessage, Throwable cause) {
        super(logMessage, cause);
        this.failure = failure;
    }

    public AiFailure failure() {
        return failure;
    }
}

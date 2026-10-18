package com.forgeai.common.exception;

import java.time.Duration;

/**
 * The caller has hit a limit (AI allowance, failed sign-ins). Mapped to HTTP 429 with a
 * {@code Retry-After} header; the message is shown to users.
 */
public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    /** "1 minute", "12 minutes", "3 hours", for messages. */
    public static String describe(Duration duration) {
        long minutes = Math.max(1, (duration.toSeconds() + 59) / 60);
        if (minutes < 120) return minutes + (minutes == 1 ? " minute" : " minutes");
        long hours = (minutes + 59) / 60;
        return hours + " hours";
    }
}

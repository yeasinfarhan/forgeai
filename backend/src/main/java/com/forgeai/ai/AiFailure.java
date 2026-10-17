package com.forgeai.ai;

import org.springframework.http.HttpStatus;

/** Why an AI call failed, with the HTTP status and the message safe to show to users. */
public enum AiFailure {

    NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE,
            "AI features are not configured. Set the API key for AI_PROVIDER in .env "
                    + "(CLAUDE_API_KEY or GEMINI_API_KEY) and restart the backend."),
    AUTHENTICATION_FAILED(HttpStatus.SERVICE_UNAVAILABLE,
            "The AI service rejected the configured API key. Check CLAUDE_API_KEY or GEMINI_API_KEY in .env."),
    RATE_LIMITED(HttpStatus.SERVICE_UNAVAILABLE,
            "The AI service is busy right now. Please try again in a minute."),
    UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "The AI service is temporarily unavailable. Please try again."),
    TIMEOUT(HttpStatus.GATEWAY_TIMEOUT,
            "The AI service took too long to respond. Please try again."),
    INVALID_REQUEST(HttpStatus.BAD_GATEWAY,
            "The AI service could not process the request."),
    BAD_RESPONSE(HttpStatus.BAD_GATEWAY,
            "The AI service returned an unexpected response. Please try again."),
    REFUSED(HttpStatus.UNPROCESSABLE_ENTITY,
            "The AI declined to process this request. Try rephrasing your input.");

    private final HttpStatus httpStatus;
    private final String userMessage;

    AiFailure(HttpStatus httpStatus, String userMessage) {
        this.httpStatus = httpStatus;
        this.userMessage = userMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String userMessage() {
        return userMessage;
    }
}

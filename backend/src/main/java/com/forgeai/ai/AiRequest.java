package com.forgeai.ai;

import java.util.Map;
import java.util.Objects;

/**
 * One single-turn request to the configured AI provider.
 *
 * @param purpose     short label for logs and usage tracking, e.g. {@code "idea-intake.turn"}
 * @param system      system prompt
 * @param userMessage the user turn
 * @param jsonSchema  if set, the model must answer with JSON matching this schema (structured outputs)
 * @param maxTokens   output limit for this request; null uses the configured {@code forgeai.ai.max-tokens}
 */
public record AiRequest(String purpose, String system, String userMessage, Map<String, Object> jsonSchema, Integer maxTokens) {

    public AiRequest {
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(userMessage, "userMessage");
    }

    public AiRequest(String purpose, String system, String userMessage, Map<String, Object> jsonSchema) {
        this(purpose, system, userMessage, jsonSchema, null);
    }

    /** The output limit to send: this request's own, or the configured default. */
    public int maxTokensOr(int configured) {
        return maxTokens == null ? configured : maxTokens;
    }
}

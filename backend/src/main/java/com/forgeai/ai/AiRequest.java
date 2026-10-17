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
 */
public record AiRequest(String purpose, String system, String userMessage, Map<String, Object> jsonSchema) {

    public AiRequest {
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(userMessage, "userMessage");
    }
}

package com.forgeai.ai;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * AI settings, bound from {@code forgeai.ai.*}.
 *
 * <p>API keys are optional at startup so the rest of the application works without them; AI
 * features report "not configured" until the selected provider's key is set.
 *
 * @param provider   which provider answers AI requests
 * @param claude     Anthropic Claude settings
 * @param gemini     Google Gemini settings
 * @param maxTokens  upper bound for one response, including the model's thinking
 * @param timeout    how long to wait for one response
 * @param maxRetries retries for rate limits, overload and transient server errors
 */
@Validated
@ConfigurationProperties("forgeai.ai")
public record AiProperties(
        @NotNull Provider provider,
        @Valid @NotNull ProviderSettings claude,
        @Valid @NotNull ProviderSettings gemini,
        @Min(256) int maxTokens,
        @NotNull Duration timeout,
        @Min(0) int maxRetries) {

    public enum Provider {
        CLAUDE,
        GEMINI
    }

    public record ProviderSettings(String apiKey, @NotBlank String model, @NotBlank String baseUrl) {

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}

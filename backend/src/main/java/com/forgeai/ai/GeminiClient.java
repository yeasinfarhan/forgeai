package com.forgeai.ai;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Google Gemini via {@code POST /v1beta/models/{model}:generateContent}. JSON answers use
 * {@code responseMimeType: application/json} with a {@code responseSchema} converted from the
 * feature's JSON Schema by {@link GeminiSchemas}. Active when {@code AI_PROVIDER=gemini}.
 *
 * <p>Note: on Gemini's free tier, Google may use prompts and responses to improve its products.
 */
@Component
@ConditionalOnProperty(name = "forgeai.ai.provider", havingValue = "gemini")
public class GeminiClient extends HttpAiClient {

    /** Finish reasons meaning the model would not (fully) answer for policy reasons. */
    private static final Set<String> BLOCKED_FINISH_REASONS =
            Set.of("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "LANGUAGE");

    private final RestClient restClient;

    @Autowired
    public GeminiClient(AiProperties properties) {
        this(properties, RestClient.builder().requestFactory(requestFactory(properties.timeout())), Sleeper.THREAD);
    }

    GeminiClient(AiProperties properties, RestClient.Builder builder, Sleeper sleeper) {
        super(properties, sleeper);
        this.restClient = builder.baseUrl(properties.gemini().baseUrl()).build();
    }

    @Override
    protected String provider() {
        return "gemini";
    }

    @Override
    protected AiProperties.ProviderSettings settings() {
        return properties.gemini();
    }

    /** Gemini reports an invalid key as 400 INVALID_ARGUMENT with reason API_KEY_INVALID. */
    @Override
    protected AiFailure classify(int status, String body) {
        if (body != null && (body.contains("API_KEY_INVALID") || body.contains("API key not valid"))) {
            return AiFailure.AUTHENTICATION_FAILED;
        }
        return super.classify(status, body);
    }

    @Override
    protected AiResult send(AiRequest request) {
        boolean json = request.jsonSchema() != null;
        GeminiApi.Request body = new GeminiApi.Request(
                request.system() == null ? null : GeminiApi.Content.of(null, request.system()),
                List.of(GeminiApi.Content.of("user", request.userMessage())),
                new GeminiApi.GenerationConfig(
                        request.maxTokensOr(properties.maxTokens()),
                        json ? "application/json" : null,
                        json ? GeminiSchemas.toResponseSchema(request.jsonSchema()) : null));

        GeminiApi.Response response = restClient.post()
                .uri("/v1beta/models/{model}:generateContent", settings().model())
                .header("x-goog-api-key", settings().apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(GeminiApi.Response.class);
        return toResult(request, response);
    }

    private AiResult toResult(AiRequest request, GeminiApi.Response response) {
        if (response == null) {
            throw new AiException(AiFailure.BAD_RESPONSE, "Gemini returned an empty response");
        }
        if (response.candidates() == null || response.candidates().isEmpty()) {
            if (response.promptFeedback() != null && response.promptFeedback().blockReason() != null) {
                throw new AiException(AiFailure.REFUSED,
                        "Gemini blocked the prompt (" + response.promptFeedback().blockReason() + ") for purpose=" + request.purpose());
            }
            throw new AiException(AiFailure.BAD_RESPONSE, "Gemini returned no candidates for purpose=" + request.purpose());
        }

        GeminiApi.Candidate candidate = response.candidates().getFirst();
        String finishReason = candidate.finishReason();
        if ("MAX_TOKENS".equals(finishReason)) {
            throw new AiException(AiFailure.BAD_RESPONSE,
                    "Gemini hit maxOutputTokens=" + request.maxTokensOr(properties.maxTokens()) + " for purpose=" + request.purpose());
        }
        if (finishReason != null && BLOCKED_FINISH_REASONS.contains(finishReason)) {
            throw new AiException(AiFailure.REFUSED, "Gemini stopped with " + finishReason + " for purpose=" + request.purpose());
        }

        List<GeminiApi.Part> parts = candidate.content() == null || candidate.content().parts() == null
                ? List.of()
                : candidate.content().parts();
        String text = parts.stream()
                .filter(part -> !Boolean.TRUE.equals(part.thought()) && part.text() != null)
                .map(GeminiApi.Part::text)
                .collect(Collectors.joining());
        if (text.isBlank()) {
            throw new AiException(AiFailure.BAD_RESPONSE, "Gemini returned no text for purpose=" + request.purpose());
        }

        GeminiApi.UsageMetadata usage = response.usageMetadata();
        int inputTokens = usage == null ? 0 : Objects.requireNonNullElse(usage.promptTokenCount(), 0);
        int outputTokens = usage == null ? 0
                : Objects.requireNonNullElse(usage.candidatesTokenCount(), 0) + Objects.requireNonNullElse(usage.thoughtsTokenCount(), 0);
        return new AiResult(text, Objects.requireNonNullElse(response.modelVersion(), settings().model()), inputTokens, outputTokens);
    }
}

package com.forgeai.ai;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Anthropic Claude via the Messages API ({@code POST /v1/messages}). JSON answers use structured
 * outputs ({@code output_config.format}); forced tool use is avoided because current models reject it.
 * Active when {@code AI_PROVIDER=claude} (the default).
 */
@Component
@ConditionalOnProperty(name = "forgeai.ai.provider", havingValue = "claude", matchIfMissing = true)
public class ClaudeClient extends HttpAiClient {

    static final String API_VERSION = "2023-06-01";

    private final RestClient restClient;

    @Autowired
    public ClaudeClient(AiProperties properties) {
        this(properties, RestClient.builder().requestFactory(requestFactory(properties.timeout())), Sleeper.THREAD);
    }

    ClaudeClient(AiProperties properties, RestClient.Builder builder, Sleeper sleeper) {
        super(properties, sleeper);
        this.restClient = builder
                .baseUrl(properties.claude().baseUrl())
                .defaultHeader("anthropic-version", API_VERSION)
                .build();
    }

    @Override
    protected String provider() {
        return "claude";
    }

    @Override
    protected AiProperties.ProviderSettings settings() {
        return properties.claude();
    }

    @Override
    protected AiResult send(AiRequest request) {
        MessagesApi.Request body = new MessagesApi.Request(
                settings().model(),
                request.maxTokensOr(properties.maxTokens()),
                request.system(),
                List.of(new MessagesApi.Message("user", request.userMessage())),
                request.jsonSchema() == null
                        ? null
                        : new MessagesApi.OutputConfig(MessagesApi.OutputFormat.jsonSchema(request.jsonSchema())));

        MessagesApi.Response response = restClient.post()
                .uri("/v1/messages")
                .header("x-api-key", settings().apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MessagesApi.Response.class);
        return toResult(request, response);
    }

    private AiResult toResult(AiRequest request, MessagesApi.Response response) {
        if (response == null || response.content() == null) {
            throw new AiException(AiFailure.BAD_RESPONSE, "Claude returned an empty response");
        }
        if ("refusal".equals(response.stopReason())) {
            throw new AiException(AiFailure.REFUSED, "Claude refused purpose=" + request.purpose());
        }
        if ("max_tokens".equals(response.stopReason())) {
            throw new AiException(AiFailure.BAD_RESPONSE,
                    "Claude hit max_tokens=" + request.maxTokensOr(properties.maxTokens()) + " for purpose=" + request.purpose());
        }

        String text = response.content().stream()
                .filter(block -> "text".equals(block.type()) && block.text() != null)
                .map(MessagesApi.ContentBlock::text)
                .collect(Collectors.joining());
        if (text.isBlank()) {
            throw new AiException(AiFailure.BAD_RESPONSE, "Claude returned no text for purpose=" + request.purpose());
        }

        int inputTokens = response.usage() == null ? 0 : Objects.requireNonNullElse(response.usage().inputTokens(), 0);
        int outputTokens = response.usage() == null ? 0 : Objects.requireNonNullElse(response.usage().outputTokens(), 0);
        return new AiResult(text, Objects.requireNonNullElse(response.model(), settings().model()), inputTokens, outputTokens);
    }
}

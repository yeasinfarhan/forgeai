package com.forgeai.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ClaudeClientTest {

    private static final String URL = "https://api.anthropic.com/v1/messages";

    private static final AiProperties PROPERTIES = properties("test-key");

    static AiProperties properties(String claudeKey) {
        return new AiProperties(
                AiProperties.Provider.CLAUDE,
                new AiProperties.ProviderSettings(claudeKey, "claude-sonnet-5-5", "https://api.anthropic.com"),
                new AiProperties.ProviderSettings("", "gemini-3.8-flash", "https://generativelanguage.googleapis.com"),
                1024, Duration.ofSeconds(30), 2);
    }

    private static final String OK_RESPONSE = """
            {
              "id": "msg_1",
              "type": "message",
              "role": "assistant",
              "model": "claude-sonnet-5-5",
              "content": [
                {"type": "thinking", "thinking": "Let me think.", "signature": "abc"},
                {"type": "text", "text": "{\\"ok\\": true}"}
              ],
              "stop_reason": "end_turn",
              "usage": {"input_tokens": 120, "output_tokens": 45}
            }
            """;

    private final List<Duration> sleeps = new ArrayList<>();
    private MockRestServiceServer server;
    private ClaudeClient client;

    @BeforeEach
    void setUp() {
        client = clientWith(PROPERTIES);
    }

    private ClaudeClient clientWith(AiProperties properties) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new ClaudeClient(properties, builder, sleeps::add);
    }

    private static AiRequest request() {
        return new AiRequest("test", "You are a test.", "Hello", Map.of("type", "object"));
    }

    @Test
    void sendsMessagesRequestAndReturnsTextAndUsage() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "test-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(jsonPath("$.model").value("claude-sonnet-5-5"))
                .andExpect(jsonPath("$.max_tokens").value(1024))
                .andExpect(jsonPath("$.system").value("You are a test."))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("Hello"))
                .andExpect(jsonPath("$.output_config.format.type").value("json_schema"))
                .andExpect(jsonPath("$.output_config.format.schema.type").value("object"))
                .andRespond(withSuccess(OK_RESPONSE, MediaType.APPLICATION_JSON));

        AiResult result = client.complete(request());

        assertThat(result.text()).isEqualTo("{\"ok\": true}");
        assertThat(result.model()).isEqualTo("claude-sonnet-5-5");
        assertThat(result.inputTokens()).isEqualTo(120);
        assertThat(result.outputTokens()).isEqualTo(45);
        server.verify();
    }

    @Test
    void omitsOutputConfigWhenNoSchemaIsGiven() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.output_config").doesNotExist())
                .andRespond(withSuccess(OK_RESPONSE, MediaType.APPLICATION_JSON));

        client.complete(new AiRequest("test", null, "Hello", null));
        server.verify();
    }

    @Test
    void retriesOverloadedThenSucceeds() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatusCode.valueOf(529)));
        server.expect(requestTo(URL)).andRespond(withSuccess(OK_RESPONSE, MediaType.APPLICATION_JSON));

        assertThat(client.complete(request()).text()).isEqualTo("{\"ok\": true}");
        assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
        server.verify();
    }

    @Test
    void honoursRetryAfterAndGivesUpAfterMaxRetries() {
        server.expect(times(3), requestTo(URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("retry-after", "3"));

        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOfSatisfying(AiException.class,
                        ex -> assertThat(ex.failure()).isEqualTo(AiFailure.RATE_LIMITED));
        assertThat(sleeps).containsExactly(Duration.ofSeconds(3), Duration.ofSeconds(3));
        server.verify();
    }

    @Test
    void doesNotRetryAuthenticationErrors() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}"));

        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOfSatisfying(AiException.class,
                        ex -> assertThat(ex.failure()).isEqualTo(AiFailure.AUTHENTICATION_FAILED));
        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    void reportsRefusal() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                OK_RESPONSE.replace("\"end_turn\"", "\"refusal\""), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOfSatisfying(AiException.class,
                        ex -> assertThat(ex.failure()).isEqualTo(AiFailure.REFUSED));
    }

    @Test
    void reportsTruncatedOutput() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                OK_RESPONSE.replace("\"end_turn\"", "\"max_tokens\""), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOfSatisfying(AiException.class,
                        ex -> assertThat(ex.failure()).isEqualTo(AiFailure.BAD_RESPONSE));
    }

    @Test
    void failsFastWithoutApiKey() {
        ClaudeClient unconfigured = clientWith(properties(""));

        assertThatThrownBy(() -> unconfigured.complete(request()))
                .isInstanceOfSatisfying(AiException.class,
                        ex -> assertThat(ex.failure()).isEqualTo(AiFailure.NOT_CONFIGURED));
        server.verify(); // no HTTP request was made
    }

    @Test
    void backoffGrowsExponentiallyAndIsCapped() {
        assertThat(HttpAiClient.backoff(1, null)).isEqualTo(Duration.ofSeconds(1));
        assertThat(HttpAiClient.backoff(2, null)).isEqualTo(Duration.ofSeconds(2));
        assertThat(HttpAiClient.backoff(3, null)).isEqualTo(Duration.ofSeconds(4));
        assertThat(HttpAiClient.backoff(10, null)).isEqualTo(Duration.ofSeconds(16));
    }
}

package com.forgeai.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiClientTest {

    private static final String URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent";

    private static final String OK_RESPONSE = """
            {
              "candidates": [{
                "content": {"role": "model", "parts": [
                  {"text": "Thinking about it…", "thought": true},
                  {"text": "{\\"ok\\": true}"}
                ]},
                "finishReason": "STOP"
              }],
              "usageMetadata": {"promptTokenCount": 80, "candidatesTokenCount": 20, "thoughtsTokenCount": 15},
              "modelVersion": "gemini-3.8-flash-001"
            }
            """;

    private final List<Duration> sleeps = new ArrayList<>();
    private MockRestServiceServer server;
    private GeminiClient client;

    static AiProperties properties(String geminiKey) {
        return new AiProperties(
                AiProperties.Provider.GEMINI,
                new AiProperties.ProviderSettings("", "claude-sonnet-5-5", "https://api.anthropic.com"),
                new AiProperties.ProviderSettings(geminiKey, "gemini-3.8-flash", "https://generativelanguage.googleapis.com"),
                2048, Duration.ofSeconds(30), 2);
    }

    @BeforeEach
    void setUp() {
        client = clientWith(properties("test-gemini-key"));
    }

    private GeminiClient clientWith(AiProperties properties) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new GeminiClient(properties, builder, sleeps::add);
    }

    private static AiRequest request() {
        return new AiRequest("test", "You are a test.", "Hello", Map.of(
                "type", "object",
                "properties", Map.of("ok", Map.of("type", "boolean")),
                "required", List.of("ok"),
                "additionalProperties", false));
    }

    @Test
    void sendsGenerateContentRequestAndSkipsThoughtParts() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-gemini-key"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("You are a test."))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Hello"))
                .andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(2048))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.type").value("OBJECT"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.properties.ok.type").value("BOOLEAN"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.additionalProperties").doesNotExist())
                .andRespond(withSuccess(OK_RESPONSE, MediaType.APPLICATION_JSON));

        AiResult result = client.complete(request());

        assertThat(result.text()).isEqualTo("{\"ok\": true}");
        assertThat(result.model()).isEqualTo("gemini-3.8-flash-001");
        assertThat(result.inputTokens()).isEqualTo(80);
        assertThat(result.outputTokens()).isEqualTo(35);
        server.verify();
    }

    @Test
    void invalidKeyIsReportedAsAuthenticationFailure() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"error": {"code": 400, "message": "API key not valid. Please pass a valid API key.",
                         "status": "INVALID_ARGUMENT", "details": [{"reason": "API_KEY_INVALID"}]}}
                        """));

        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOfSatisfying(AiException.class,
                        ex -> assertThat(ex.failure()).isEqualTo(AiFailure.AUTHENTICATION_FAILED));
        assertThat(sleeps).isEmpty();
    }

    @Test
    void rateLimitIsRetriedThenSucceeds() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(URL)).andRespond(withSuccess(OK_RESPONSE, MediaType.APPLICATION_JSON));

        assertThat(client.complete(request()).text()).isEqualTo("{\"ok\": true}");
        assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
    }

    @Test
    void safetyStopIsReportedAsRefusal() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                OK_RESPONSE.replace("\"STOP\"", "\"SAFETY\""), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOfSatisfying(AiException.class,
                        ex -> assertThat(ex.failure()).isEqualTo(AiFailure.REFUSED));
    }

    @Test
    void blockedPromptIsReportedAsRefusal() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"promptFeedback\": {\"blockReason\": \"SAFETY\"}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOfSatisfying(AiException.class,
                        ex -> assertThat(ex.failure()).isEqualTo(AiFailure.REFUSED));
    }

    @Test
    void truncatedOutputIsReportedAsBadResponse() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                OK_RESPONSE.replace("\"STOP\"", "\"MAX_TOKENS\""), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOfSatisfying(AiException.class,
                        ex -> assertThat(ex.failure()).isEqualTo(AiFailure.BAD_RESPONSE));
    }

    @Test
    void failsFastWithoutApiKey() {
        GeminiClient unconfigured = clientWith(properties(""));

        assertThatThrownBy(() -> unconfigured.complete(request()))
                .isInstanceOfSatisfying(AiException.class,
                        ex -> assertThat(ex.failure()).isEqualTo(AiFailure.NOT_CONFIGURED));
        server.verify();
    }
}

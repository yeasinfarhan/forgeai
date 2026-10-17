package com.forgeai.ai;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Wire format of Gemini's {@code generateContent} endpoint. Only the fields ForgeAI uses. */
final class GeminiApi {

    private GeminiApi() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Request(Content systemInstruction, List<Content> contents, GenerationConfig generationConfig) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(String role, List<Part> parts) {

        static Content of(String role, String text) {
            return new Content(role, List.of(new Part(text, null)));
        }
    }

    /** A text part. {@code thought} is true for the model's thinking summaries, which ForgeAI skips. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(String text, Boolean thought) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GenerationConfig(Integer maxOutputTokens, String responseMimeType, Map<String, Object> responseSchema) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(List<Candidate> candidates, PromptFeedback promptFeedback, UsageMetadata usageMetadata, String modelVersion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(Content content, String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PromptFeedback(String blockReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UsageMetadata(Integer promptTokenCount, Integer candidatesTokenCount, Integer thoughtsTokenCount) {
    }
}

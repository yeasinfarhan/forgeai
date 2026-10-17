package com.forgeai.ai;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Wire format of the Claude Messages API ({@code POST /v1/messages}). Only the fields ForgeAI uses. */
final class MessagesApi {

    private MessagesApi() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Request(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            String system,
            List<Message> messages,
            @JsonProperty("output_config") OutputConfig outputConfig) {
    }

    record Message(String role, String content) {
    }

    record OutputConfig(OutputFormat format) {
    }

    record OutputFormat(String type, Map<String, Object> schema) {

        static OutputFormat jsonSchema(Map<String, Object> schema) {
            return new OutputFormat("json_schema", schema);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(
            String id,
            String model,
            List<ContentBlock> content,
            @JsonProperty("stop_reason") String stopReason,
            Usage usage) {
    }

    /** Text, thinking and other block types share this shape; ForgeAI only reads text blocks. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentBlock(String type, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(
            @JsonProperty("input_tokens") Integer inputTokens,
            @JsonProperty("output_tokens") Integer outputTokens) {
    }
}

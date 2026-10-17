package com.forgeai.ai;

/**
 * The model's answer.
 *
 * @param text         the text content (a JSON document when the request had a schema)
 * @param model        the model that produced it
 * @param inputTokens  tokens billed for the input
 * @param outputTokens tokens billed for the output (including thinking)
 */
public record AiResult(String text, String model, int inputTokens, int outputTokens) {
}

package com.forgeai.ai;

/**
 * A model provider (Claude or Gemini), chosen by {@code AI_PROVIDER}. Feature code never calls a
 * provider directly; it uses {@link AiClient}, which adds usage limits and metrics around it.
 */
public interface AiProvider {

    /** Short provider name for logs and metrics, e.g. {@code "claude"}. */
    String name();

    /**
     * Sends one single-turn request.
     *
     * @throws AiException if the provider is not configured, unreachable, or returns an unusable answer
     */
    AiResult complete(AiRequest request);
}

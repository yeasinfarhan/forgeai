package com.forgeai.ai;

/**
 * The single entry point for calling a language model. Feature code depends only on this
 * interface; which provider answers (Claude or Gemini) is chosen by {@code AI_PROVIDER}, and every
 * call is checked against the caller's usage limits and measured (see {@link MeteredAiClient}).
 */
public interface AiClient {

    /**
     * Sends one single-turn request.
     *
     * @throws AiException if the provider is not configured, unreachable, or returns an unusable answer
     */
    AiResult complete(AiRequest request);
}

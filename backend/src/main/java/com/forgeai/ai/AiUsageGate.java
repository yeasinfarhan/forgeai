package com.forgeai.ai;

import java.time.Duration;

/**
 * Hook around every AI call, implemented outside the AI module (usage tracking and limits), so the
 * AI module does not depend on users, projects or the database.
 */
public interface AiUsageGate {

    /**
     * Called before a request is sent. Throws to refuse it, for example when the caller has used up
     * their allowance.
     */
    void beforeCall(AiRequest request);

    /**
     * Called after every call that was sent.
     *
     * @param result  the answer, or null if the call failed
     * @param failure why it failed, or null if it succeeded
     */
    void afterCall(AiRequest request, String provider, AiResult result, AiFailure failure, Duration duration);
}

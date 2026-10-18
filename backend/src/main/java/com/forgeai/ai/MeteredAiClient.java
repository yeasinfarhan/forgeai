package com.forgeai.ai;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;

/**
 * The {@link AiClient} feature code uses: asks the {@link AiUsageGate}s whether the call may go
 * ahead, calls the configured {@link AiProvider}, then reports the outcome to the gates and to the
 * metrics ({@code forgeai.ai.requests} timer and {@code forgeai.ai.tokens} counter, tagged by
 * provider, purpose and outcome).
 */
@Component
class MeteredAiClient implements AiClient {

    private final AiProvider provider;
    private final List<AiUsageGate> gates;
    private final MeterRegistry meters;

    MeteredAiClient(AiProvider provider, ObjectProvider<AiUsageGate> gates, ObjectProvider<MeterRegistry> meters) {
        this.provider = provider;
        this.gates = gates.orderedStream().toList();
        this.meters = meters.getIfAvailable(() -> Metrics.globalRegistry);
    }

    @Override
    public AiResult complete(AiRequest request) {
        gates.forEach(gate -> gate.beforeCall(request));
        long startedAt = System.nanoTime();
        AiResult result = null;
        AiFailure failure = null;
        try {
            result = provider.complete(request);
            return result;
        } catch (AiException ex) {
            failure = ex.failure();
            throw ex;
        } catch (RuntimeException ex) {
            failure = AiFailure.UNAVAILABLE;
            throw ex;
        } finally {
            Duration duration = Duration.ofNanos(System.nanoTime() - startedAt);
            record(request, result, failure, duration);
            for (AiUsageGate gate : gates) gate.afterCall(request, provider.name(), result, failure, duration);
        }
    }

    private void record(AiRequest request, AiResult result, AiFailure failure, Duration duration) {
        String outcome = failure == null ? "success" : failure.name().toLowerCase(java.util.Locale.ROOT);
        Timer.builder("forgeai.ai.requests")
                .description("AI calls, by provider, purpose and outcome")
                .tag("provider", provider.name())
                .tag("purpose", request.purpose())
                .tag("outcome", outcome)
                .register(meters)
                .record(duration);
        if (result != null) {
            tokens(request, "input").increment(result.inputTokens());
            tokens(request, "output").increment(result.outputTokens());
        }
    }

    private Counter tokens(AiRequest request, String direction) {
        return Counter.builder("forgeai.ai.tokens")
                .description("Tokens billed by the AI provider")
                .tag("provider", provider.name())
                .tag("purpose", request.purpose())
                .tag("direction", direction)
                .register(meters);
    }
}

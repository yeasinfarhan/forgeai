package com.forgeai.ai;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Behaviour shared by every HTTP-based provider: the "not configured" check, retries with
 * exponential backoff, error translation and usage logging. Subclasses only build the
 * provider's request and read its response ({@link #send}).
 *
 * <p>Retries: rate limits (429), overload (529) and transient server errors (500/502/503/504)
 * are retried, honouring {@code Retry-After}. Connection failures are retried; read timeouts are
 * not, because the request may still be running on the provider's side.
 */
abstract class HttpAiClient implements AiClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(20);
    private static final Logger log = LoggerFactory.getLogger(HttpAiClient.class);

    protected final AiProperties properties;
    private final Sleeper sleeper;

    protected HttpAiClient(AiProperties properties, Sleeper sleeper) {
        this.properties = properties;
        this.sleeper = sleeper;
    }

    /** Short provider name for logs, e.g. {@code "claude"}. */
    protected abstract String provider();

    protected abstract AiProperties.ProviderSettings settings();

    /**
     * Makes one HTTP attempt and converts the response.
     *
     * @throws RestClientResponseException for HTTP error statuses (retried or translated here)
     * @throws ResourceAccessException     for I/O failures (retried or translated here)
     * @throws AiException                 for answers that are unusable (not retried)
     */
    protected abstract AiResult send(AiRequest request);

    /** Maps an HTTP error status (and body) to a failure. Providers can refine this. */
    protected AiFailure classify(int status, String body) {
        return switch (status) {
            case 401, 403 -> AiFailure.AUTHENTICATION_FAILED;
            case 429 -> AiFailure.RATE_LIMITED;
            case 400, 404, 413, 422 -> AiFailure.INVALID_REQUEST;
            default -> AiFailure.UNAVAILABLE;
        };
    }

    @Override
    public final AiResult complete(AiRequest request) {
        if (!settings().isConfigured()) {
            throw new AiException(AiFailure.NOT_CONFIGURED, provider() + " API key is not set");
        }

        long startedAt = System.nanoTime();
        for (int attempt = 1; ; attempt++) {
            try {
                AiResult result = send(request);
                log.info("AI call provider={} purpose={} model={} inputTokens={} outputTokens={} durationMs={} attempts={}",
                        provider(), request.purpose(), result.model(), result.inputTokens(), result.outputTokens(),
                        (System.nanoTime() - startedAt) / 1_000_000, attempt);
                return result;

            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (isRetryable(status) && attempt <= properties.maxRetries()) {
                    Duration delay = backoff(attempt, ex.getResponseHeaders());
                    log.warn("AI call provider={} purpose={} got HTTP {}; retrying in {} ms (attempt {}/{})",
                            provider(), request.purpose(), status, delay.toMillis(), attempt, properties.maxRetries() + 1);
                    pause(delay);
                    continue;
                }
                String body = ex.getResponseBodyAsString();
                throw new AiException(classify(status, body),
                        provider() + " call purpose=" + request.purpose() + " failed with HTTP " + status + ": " + abbreviate(body),
                        ex);

            } catch (ResourceAccessException ex) {
                if (isTimeout(ex)) {
                    throw new AiException(AiFailure.TIMEOUT,
                            provider() + " call purpose=" + request.purpose() + " timed out after " + properties.timeout(), ex);
                }
                if (attempt <= properties.maxRetries()) {
                    Duration delay = backoff(attempt, null);
                    log.warn("AI call provider={} purpose={} failed to connect ({}); retrying in {} ms",
                            provider(), request.purpose(), ex.getMessage(), delay.toMillis());
                    pause(delay);
                    continue;
                }
                throw new AiException(AiFailure.UNAVAILABLE,
                        provider() + " call purpose=" + request.purpose() + " could not reach the API", ex);
            }
        }
    }

    private static boolean isRetryable(int status) {
        return status == 429 || status == 529 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    private static boolean isTimeout(ResourceAccessException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof java.net.SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /** Exponential backoff (1 s, 2 s, 4 s …), or the server's Retry-After, capped at 20 s. */
    static Duration backoff(int attempt, HttpHeaders headers) {
        if (headers != null) {
            String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
            if (retryAfter != null) {
                try {
                    Duration requested = Duration.ofSeconds(Long.parseLong(retryAfter.trim()));
                    return requested.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : requested;
                } catch (NumberFormatException ignored) {
                    // Retry-After can also be an HTTP date; fall back to exponential backoff.
                }
            }
        }
        Duration exponential = Duration.ofSeconds(1L << Math.min(attempt - 1, 4));
        return exponential.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : exponential;
    }

    private void pause(Duration delay) {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiException(AiFailure.UNAVAILABLE, "Interrupted while waiting to retry AI call", ex);
        }
    }

    protected static String abbreviate(String text) {
        if (text == null) return "";
        return text.length() <= 500 ? text : text.substring(0, 500) + "…";
    }

    protected static JdkClientHttpRequestFactory requestFactory(Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    /** Waiting between retries, replaceable in tests. */
    @FunctionalInterface
    interface Sleeper {
        Sleeper THREAD = Thread::sleep;

        void sleep(Duration duration) throws InterruptedException;
    }
}

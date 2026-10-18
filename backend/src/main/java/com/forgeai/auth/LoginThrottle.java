package com.forgeai.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.forgeai.common.exception.TooManyRequestsException;

/**
 * Slows down password guessing: after too many failed sign-ins for one email, or from one client
 * address, within {@value #WINDOW_MINUTES} minutes, further attempts are refused until the oldest
 * failure leaves the window. A successful sign-in clears the email's failures.
 *
 * <p>Kept in memory: limits apply per backend instance and reset on restart. The same message is
 * used for both limits, so it does not reveal whether an email is registered.
 */
@Component
class LoginThrottle {

    static final int WINDOW_MINUTES = 15;
    static final Duration WINDOW = Duration.ofMinutes(WINDOW_MINUTES);
    /** Above this many tracked keys, expired entries are swept on the next failure. */
    private static final int SWEEP_THRESHOLD = 10_000;

    private final int maxPerEmail;
    private final int maxPerAddress;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    @Autowired
    LoginThrottle(
            @Value("${forgeai.login.max-failures-per-email:10}") int maxPerEmail,
            @Value("${forgeai.login.max-failures-per-address:50}") int maxPerAddress) {
        this(maxPerEmail, maxPerAddress, Clock.systemUTC());
    }

    LoginThrottle(int maxPerEmail, int maxPerAddress, Clock clock) {
        this.maxPerEmail = maxPerEmail;
        this.maxPerAddress = maxPerAddress;
        this.clock = clock;
    }

    /** Refuses the attempt if the email or the address has too many recent failures. */
    void check(String email, String address) {
        Instant now = clock.instant();
        Duration wait = waitFor(emailKey(email), maxPerEmail, now);
        if (wait == null && address != null) wait = waitFor(addressKey(address), maxPerAddress, now);
        if (wait != null) {
            throw new TooManyRequestsException("Too many failed sign-in attempts. Try again in "
                    + TooManyRequestsException.describe(wait) + ".", wait);
        }
    }

    void failed(String email, String address) {
        Instant now = clock.instant();
        if (failures.size() > SWEEP_THRESHOLD) sweep(now);
        add(emailKey(email), now);
        if (address != null) add(addressKey(address), now);
    }

    void succeeded(String email) {
        failures.remove(emailKey(email));
    }

    private Duration waitFor(String key, int max, Instant now) {
        if (max <= 0) return null;
        Deque<Instant> times = failures.get(key);
        if (times == null) return null;
        synchronized (times) {
            prune(times, now);
            if (times.size() < max) return null;
            return Duration.between(now, times.peekFirst().plus(WINDOW));
        }
    }

    private void add(String key, Instant now) {
        Deque<Instant> times = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            prune(times, now);
            times.addLast(now);
        }
    }

    private void sweep(Instant now) {
        failures.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                prune(entry.getValue(), now);
                return entry.getValue().isEmpty();
            }
        });
    }

    private static void prune(Deque<Instant> times, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) times.removeFirst();
    }

    private static String emailKey(String email) {
        return "email:" + email;
    }

    private static String addressKey(String address) {
        return "address:" + address;
    }
}

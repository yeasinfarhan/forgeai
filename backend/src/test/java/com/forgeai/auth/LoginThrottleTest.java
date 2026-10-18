package com.forgeai.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.forgeai.common.exception.TooManyRequestsException;

class LoginThrottleTest {

    private final MutableClock clock = new MutableClock();

    @Test
    void tooManyFailuresForOneEmailAreRefusedUntilTheyExpire() {
        LoginThrottle throttle = new LoginThrottle(3, 100, clock);
        for (int i = 0; i < 3; i++) {
            throttle.check("ada@example.com", "10.0.0.1");
            throttle.failed("ada@example.com", "10.0.0.1");
            clock.advance(Duration.ofMinutes(1));
        }
        assertThatThrownBy(() -> throttle.check("ada@example.com", "10.0.0.2"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage("Too many failed sign-in attempts. Try again in 12 minutes.")
                .satisfies(ex -> assertThat(((TooManyRequestsException) ex).retryAfter()).isEqualTo(Duration.ofMinutes(12)));
        assertThatCode(() -> throttle.check("bob@example.com", "10.0.0.1")).doesNotThrowAnyException();

        clock.advance(Duration.ofMinutes(12));
        assertThatCode(() -> throttle.check("ada@example.com", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void tooManyFailuresFromOneAddressAreRefusedAndSuccessClearsTheEmail() {
        LoginThrottle throttle = new LoginThrottle(2, 3, clock);
        throttle.failed("a@example.com", "10.0.0.1");
        throttle.failed("b@example.com", "10.0.0.1");
        throttle.failed("c@example.com", "10.0.0.1");
        assertThatThrownBy(() -> throttle.check("d@example.com", "10.0.0.1")).isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> throttle.check("d@example.com", "10.0.0.9")).doesNotThrowAnyException();

        throttle.failed("e@example.com", "10.0.0.9");
        throttle.succeeded("e@example.com");
        throttle.failed("e@example.com", "10.0.0.9");
        assertThatCode(() -> throttle.check("e@example.com", "10.0.0.9")).doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

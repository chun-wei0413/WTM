package com.memehub.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(10);

    /** A clock the test can move forward. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

    @Test
    void allowsUpToTheLimitAndThenRefuses() {
        assertThat(limiter.tryAcquire("k", 3, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("k", 3, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("k", 3, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("k", 3, WINDOW)).isFalse();
    }

    @Test
    void aRefusedAttemptIsNotRecorded() {
        limiter.tryAcquire("k", 1, WINDOW);
        limiter.tryAcquire("k", 1, WINDOW);
        clock.advance(WINDOW.plusSeconds(1));

        assertThat(limiter.tryAcquire("k", 1, WINDOW)).isTrue();
    }

    @Test
    void allowsAgainOnceTheOldEventsLeaveTheWindow() {
        limiter.tryAcquire("k", 2, WINDOW);
        clock.advance(Duration.ofMinutes(6));
        limiter.tryAcquire("k", 2, WINDOW);
        assertThat(limiter.tryAcquire("k", 2, WINDOW)).isFalse();

        clock.advance(Duration.ofMinutes(5));   // the first event is now 11 minutes old

        assertThat(limiter.tryAcquire("k", 2, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("k", 2, WINDOW)).isFalse();
    }

    @Test
    void isLimitedLooksWithoutRecording() {
        assertThat(limiter.isLimited("k", 1, WINDOW)).isFalse();
        assertThat(limiter.isLimited("k", 1, WINDOW)).isFalse();
        limiter.tryAcquire("k", 1, WINDOW);

        assertThat(limiter.isLimited("k", 1, WINDOW)).isTrue();
        clock.advance(WINDOW.plusSeconds(1));
        assertThat(limiter.isLimited("k", 1, WINDOW)).isFalse();
    }

    @Test
    void keysAreIndependentAndResetForgetsOneKey() {
        limiter.tryAcquire("a", 1, WINDOW);
        limiter.tryAcquire("b", 1, WINDOW);

        limiter.reset("a");

        assertThat(limiter.isLimited("a", 1, WINDOW)).isFalse();
        assertThat(limiter.isLimited("b", 1, WINDOW)).isTrue();
    }

    @Test
    void neverLetsMoreThanTheLimitThroughUnderConcurrency() throws Exception {
        int limit = 50;
        AtomicInteger granted = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 16; t++) {
            Thread thread = Thread.ofPlatform().start(() -> {
                for (int i = 0; i < 100; i++) {
                    if (limiter.tryAcquire("shared", limit, WINDOW)) {
                        granted.incrementAndGet();
                    }
                }
            });
            threads.add(thread);
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(granted.get()).isEqualTo(limit);
    }

    @Test
    void forgetsKeysThatHaveBeenIdleLongerThanTheirWindow() {
        limiter.tryAcquire("old", 5, WINDOW);
        clock.advance(WINDOW.plusMinutes(1));
        limiter.tryAcquire("fresh", 5, WINDOW);

        limiter.evictIdleKeys();

        assertThat(limiter.trackedKeys()).isEqualTo(1);
        assertThat(limiter.isLimited("fresh", 1, WINDOW)).isTrue();
    }
}

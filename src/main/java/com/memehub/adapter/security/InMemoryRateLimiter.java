package com.memehub.adapter.security;

import com.memehub.application.port.out.RateLimiterPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sliding-window limiter that keeps its counters in this process. Counters are per
 * application instance; see {@link RateLimiterPort}.
 */
@Component
class InMemoryRateLimiter implements RateLimiterPort {

    /** Upper bound on tracked keys, so a flood of made-up keys cannot exhaust memory. */
    static final int MAX_KEYS = 100_000;

    /** The longest window ever asked for, used to decide when an idle key can be forgotten. */
    private volatile long longestWindowNanos = Duration.ofMinutes(1).toNanos();

    private final ConcurrentHashMap<String, Deque<Long>> events = new ConcurrentHashMap<>();
    private final Clock clock;

    InMemoryRateLimiter() {
        this(Clock.systemUTC());
    }

    InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = now();
        remember(window);
        Deque<Long> timestamps = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            prune(timestamps, now, window);
            if (timestamps.size() >= limit) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    @Override
    public boolean isLimited(String key, int limit, Duration window) {
        Deque<Long> timestamps = events.get(key);
        if (timestamps == null) {
            return false;
        }
        synchronized (timestamps) {
            prune(timestamps, now(), window);
            return timestamps.size() >= limit;
        }
    }

    @Override
    public void reset(String key) {
        events.remove(key);
    }

    /** Drops keys that have had no events for longer than any window in use. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    void evictIdleKeys() {
        long cutoff = now() - longestWindowNanos;
        events.entrySet().removeIf(entry -> {
            Deque<Long> timestamps = entry.getValue();
            synchronized (timestamps) {
                return timestamps.isEmpty() || timestamps.peekLast() < cutoff;
            }
        });
        if (events.size() > MAX_KEYS) {
            events.clear();
        }
    }

    int trackedKeys() {
        return events.size();
    }

    private void remember(Duration window) {
        long nanos = window.toNanos();
        if (nanos > longestWindowNanos) {
            longestWindowNanos = nanos;
        }
    }

    private static void prune(Deque<Long> timestamps, long now, Duration window) {
        long cutoff = now - window.toNanos();
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
            timestamps.removeFirst();
        }
    }

    private long now() {
        Instant instant = clock.instant();
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }
}

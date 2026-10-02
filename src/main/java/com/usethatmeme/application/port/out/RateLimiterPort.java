package com.usethatmeme.application.port.out;

import java.time.Duration;

/**
 * Sliding-window counter of events per key, used to slow down guessing and abuse.
 *
 * <p>The in-memory adapter counts per application instance. Running several instances
 * means each one allows its own share, so a shared store (such as Redis) would be needed
 * to enforce one limit across all of them.
 */
public interface RateLimiterPort {

    /**
     * Records one event for the key unless the key already has {@code limit} events inside the window.
     *
     * @return false when the limit was already reached (and nothing was recorded)
     */
    boolean tryAcquire(String key, int limit, Duration window);

    /** Whether the key already has {@code limit} events inside the window. Records nothing. */
    boolean isLimited(String key, int limit, Duration window);

    /** Forgets everything recorded for the key. */
    void reset(String key);
}

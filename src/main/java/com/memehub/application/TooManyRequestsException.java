package com.memehub.application;

import java.time.Duration;

/**
 * The caller is sending requests faster than allowed, or has used up an allowance.
 */
public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    /** How long to wait before trying again, or {@code null} when that cannot be told. */
    public Duration retryAfter() {
        return retryAfter;
    }
}

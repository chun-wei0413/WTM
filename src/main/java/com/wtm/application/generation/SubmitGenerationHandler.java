package com.wtm.application.generation;

import com.wtm.application.TooManyRequestsException;
import com.wtm.application.port.out.GenerationJobStore;
import com.wtm.application.port.out.GenerationJobStore.EnqueueResult;
import com.wtm.application.port.out.GenerationJobStore.QuotaLimits;
import java.time.Duration;
import java.util.UUID;

/**
 * Accepts a request to make memes for a situation. The work itself happens later,
 * in a worker, so the caller gets an id to check on instead of waiting.
 */
public class SubmitGenerationHandler {

    public static final int MAX_SITUATION_LENGTH = 300;

    private final GenerationJobStore jobs;
    private final QuotaLimits limits;

    public SubmitGenerationHandler(GenerationJobStore jobs, QuotaLimits limits) {
        this.jobs = jobs;
        this.limits = limits;
    }

    /**
     * @throws IllegalArgumentException when the description is empty or too long
     * @throws TooManyRequestsException when the user has used up an allowance
     */
    public UUID handle(UUID requesterId, String situation) {
        String text = situation == null ? "" : situation.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Please describe the situation");
        }
        if (text.codePointCount(0, text.length()) > MAX_SITUATION_LENGTH) {
            throw new IllegalArgumentException(
                    "The description must be at most " + MAX_SITUATION_LENGTH + " characters");
        }
        UUID jobId = UUID.randomUUID();
        EnqueueResult result = jobs.enqueue(jobId, requesterId, text, limits);
        return switch (result) {
            case ACCEPTED -> jobId;
            case TOO_MANY_ACTIVE -> throw new TooManyRequestsException(
                    "You already have " + limits.maxActive() + " requests in progress. Please wait for one to finish.",
                    Duration.ofSeconds(10));
            case DAILY_LIMIT_REACHED -> throw new TooManyRequestsException(
                    "You have reached today's limit of " + limits.maxPerDay() + " requests.", null);
        };
    }
}

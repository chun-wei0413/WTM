package com.memehub.application.generation;

import com.memehub.application.port.out.GenerationJobStore;
import java.util.UUID;

/**
 * Accepts a request to make memes for a situation. The work itself happens later,
 * in a worker, so the caller gets an id to check on instead of waiting.
 */
public class SubmitGenerationHandler {

    public static final int MAX_SITUATION_LENGTH = 300;

    private final GenerationJobStore jobs;

    public SubmitGenerationHandler(GenerationJobStore jobs) {
        this.jobs = jobs;
    }

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
        jobs.enqueue(jobId, requesterId, text);
        return jobId;
    }
}

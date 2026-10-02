package com.usethatmeme.application.generation;

import com.usethatmeme.application.port.out.GenerationJobStore;
import com.usethatmeme.application.port.out.GenerationJobStore.ClaimedJob;
import java.time.Duration;
import java.util.List;

/**
 * What a worker needs from the queue: take work, and rescue work that was abandoned.
 */
public class GenerationQueueHandler {

    private final GenerationJobStore jobs;

    public GenerationQueueHandler(GenerationJobStore jobs) {
        this.jobs = jobs;
    }

    public List<ClaimedJob> claim(int limit) {
        return jobs.claim(limit);
    }

    public int recoverStale(Duration runningLongerThan, int maxAttempts) {
        return jobs.recoverStale(runningLongerThan, maxAttempts);
    }
}

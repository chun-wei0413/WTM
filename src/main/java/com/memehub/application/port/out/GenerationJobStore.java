package com.memehub.application.port.out;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * The queue of generation jobs. Claiming is safe across threads and application instances.
 */
public interface GenerationJobStore {

    void enqueue(UUID jobId, UUID requesterId, String situation);

    /** Atomically moves up to {@code limit} pending jobs to running and returns them. */
    List<ClaimedJob> claim(int limit);

    void complete(UUID jobId, List<UUID> memeIds);

    void fail(UUID jobId, String reason);

    /**
     * Gives jobs that have been running for too long (a crashed worker) another chance,
     * or fails them once they have used up {@code maxAttempts}.
     *
     * @return how many jobs were touched
     */
    int recoverStale(Duration runningLongerThan, int maxAttempts);

    record ClaimedJob(UUID id, UUID requesterId, String situation, int attempts) {
    }
}

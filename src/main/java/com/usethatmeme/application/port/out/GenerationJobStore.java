package com.usethatmeme.application.port.out;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * The queue of generation jobs. Claiming is safe across threads and application instances.
 */
public interface GenerationJobStore {

    /**
     * Adds a job unless the requester has used up an allowance. Checking the allowance and
     * adding the job happen as one step, so a burst of simultaneous requests cannot slip past.
     */
    EnqueueResult enqueue(UUID jobId, UUID requesterId, String situation, QuotaLimits limits);

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

    enum EnqueueResult {
        ACCEPTED,
        TOO_MANY_ACTIVE,
        DAILY_LIMIT_REACHED
    }

    /**
     * @param maxActive most jobs a user may have waiting or running at once
     * @param maxPerDay most jobs a user may submit within 24 hours
     */
    record QuotaLimits(int maxActive, int maxPerDay) {

        public static QuotaLimits unlimited() {
            return new QuotaLimits(Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
    }

    record ClaimedJob(UUID id, UUID requesterId, String situation, int attempts) {
    }
}

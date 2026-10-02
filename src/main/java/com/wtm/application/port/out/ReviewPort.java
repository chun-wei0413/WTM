package com.wtm.application.port.out;

import com.wtm.application.report.Suggestion;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The line of reported memes waiting for the vision model to look at them again, and what it proposed.
 * Claiming is safe across threads and instances.
 */
public interface ReviewPort {

    /**
     * Puts the meme in line. Does nothing when it already is; a finished or failed review is started over
     * (the earlier proposal stays visible until the new one replaces it).
     *
     * @param forced true when an administrator asked: such a review ignores the daily budget
     */
    void request(UUID templateId, boolean forced);

    /** When the vision model last looked at the meme because of a report, if it ever did. */
    Optional<Instant> lastRunAt(UUID templateId);

    /**
     * Atomically moves up to {@code limit} waiting memes to "being looked at" and returns them. Memes that were
     * not asked for by an administrator are only taken while fewer than {@code dailyBudget} looks have happened
     * in the last 24 hours, whatever the number of reports.
     */
    List<UUID> claim(int limit, int dailyBudget);

    void complete(UUID templateId, Suggestion suggestion);

    /** Puts the meme back in line, or marks the review failed once it has used up {@code maxAttempts}. */
    void fail(UUID templateId, String error, int maxAttempts);

    /** Gives reviews that have been running for too long (a crashed worker) another chance. */
    int recoverStale(Duration runningLongerThan, int maxAttempts);

    Optional<ReviewState> find(UUID templateId);

    void delete(UUID templateId);

    /**
     * @param status PENDING, RUNNING, DONE or FAILED
     * @param suggestion the latest proposal, if there has been one
     * @param error why the last attempt failed, if it did
     * @param forced true when an administrator asked for this look; its result is theirs to act on
     */
    record ReviewState(String status, Suggestion suggestion, String error, boolean forced) {
    }
}

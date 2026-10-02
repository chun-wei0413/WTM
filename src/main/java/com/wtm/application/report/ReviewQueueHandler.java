package com.wtm.application.report;

import com.wtm.application.port.out.ReviewPort;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * What a review worker needs from the line: take work, and rescue work that was abandoned.
 */
public class ReviewQueueHandler {

    private final ReviewPort reviews;
    private final ReportPolicy policy;

    public ReviewQueueHandler(ReviewPort reviews, ReportPolicy policy) {
        this.reviews = reviews;
        this.policy = policy;
    }

    public List<UUID> claim(int limit) {
        return reviews.claim(limit, policy.dailyAnalyses());
    }

    public int recoverStale(Duration runningLongerThan, int maxAttempts) {
        return reviews.recoverStale(runningLongerThan, maxAttempts);
    }
}

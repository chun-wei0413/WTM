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

    public ReviewQueueHandler(ReviewPort reviews) {
        this.reviews = reviews;
    }

    public List<UUID> claim(int limit) {
        return reviews.claim(limit);
    }

    public int recoverStale(Duration runningLongerThan, int maxAttempts) {
        return reviews.recoverStale(runningLongerThan, maxAttempts);
    }
}

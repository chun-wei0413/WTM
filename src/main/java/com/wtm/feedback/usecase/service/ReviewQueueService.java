package com.wtm.feedback.usecase.service;

import com.wtm.feedback.entity.ReportPolicy;
import com.wtm.feedback.usecase.port.in.command.ReviewQueueUseCase;
import com.wtm.feedback.usecase.port.out.review.ReviewPort;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * What a review worker needs from the line: take work, and rescue work that was abandoned.
 */
public class ReviewQueueService implements ReviewQueueUseCase {

    private final ReviewPort reviews;
    private final ReportPolicy policy;

    public ReviewQueueService(ReviewPort reviews, ReportPolicy policy) {
        this.reviews = reviews;
        this.policy = policy;
    }

    @Override
    public List<UUID> claim(int limit) {
        return reviews.claim(limit, policy.dailyAnalyses());
    }

    @Override
    public int recoverStale(Duration runningLongerThan, int maxAttempts) {
        return reviews.recoverStale(runningLongerThan, maxAttempts);
    }
}

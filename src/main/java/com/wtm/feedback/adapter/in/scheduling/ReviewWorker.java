package com.wtm.feedback.adapter.in.scheduling;

import com.wtm.curation.adapter.in.scheduling.TaggingProperties;
import com.wtm.feedback.usecase.port.in.command.ReviewQueueUseCase;
import com.wtm.feedback.usecase.port.in.command.RunReviewUseCase;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Takes reported memes and has the vision model look at them again, one at a time, sharing the
 * tagging settings (the same model on the same graphics card).
 */
@Component
@ConditionalOnProperty(name = "wtm.tagging.worker-enabled", havingValue = "true", matchIfMissing = true)
class ReviewWorker {

    private static final Logger log = LoggerFactory.getLogger(ReviewWorker.class);

    private final ReviewQueueUseCase queue;
    private final RunReviewUseCase reviewer;
    private final TaggingProperties properties;
    private final Semaphore capacity;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    ReviewWorker(ReviewQueueUseCase queue, RunReviewUseCase reviewer, TaggingProperties properties) {
        this.queue = queue;
        this.reviewer = reviewer;
        this.properties = properties;
        this.capacity = new Semaphore(properties.maxConcurrent());
    }

    @Scheduled(fixedDelayString = "${wtm.tagging.poll-interval:PT2S}", initialDelayString = "PT3S")
    void poll() {
        int free = capacity.availablePermits();
        if (free <= 0) {
            return;
        }
        List<UUID> claimed;
        try {
            claimed = queue.claim(free);
        } catch (RuntimeException e) {
            log.warn("Could not claim reported memes to look at: {}", e.getMessage());
            return;
        }
        for (UUID id : claimed) {
            capacity.acquireUninterruptibly();
            executor.submit(() -> {
                try {
                    reviewer.handle(id);
                } finally {
                    capacity.release();
                }
            });
        }
    }

    @Scheduled(fixedDelayString = "${wtm.tagging.recovery-interval:PT1M}", initialDelayString = "PT1M")
    void recoverAbandoned() {
        try {
            int touched = queue.recoverStale(properties.timeout(), properties.maxAttempts());
            if (touched > 0) {
                log.warn("Recovered {} abandoned review(s)", touched);
            }
        } catch (RuntimeException e) {
            log.warn("Could not recover abandoned reviews: {}", e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }
}

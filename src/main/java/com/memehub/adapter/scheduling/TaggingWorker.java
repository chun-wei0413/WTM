package com.memehub.adapter.scheduling;

import com.memehub.application.collection.TagTemplateHandler;
import com.memehub.application.collection.TaggingQueueHandler;
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
 * Takes pictures waiting to be looked at and has the vision model describe them, never more than
 * {@code maxConcurrent} at once.
 */
@Component
@ConditionalOnProperty(name = "memehub.tagging.worker-enabled", havingValue = "true", matchIfMissing = true)
class TaggingWorker {

    private static final Logger log = LoggerFactory.getLogger(TaggingWorker.class);

    private final TaggingQueueHandler queue;
    private final TagTemplateHandler tagger;
    private final TaggingProperties properties;
    private final Semaphore capacity;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    TaggingWorker(TaggingQueueHandler queue, TagTemplateHandler tagger, TaggingProperties properties) {
        this.queue = queue;
        this.tagger = tagger;
        this.properties = properties;
        this.capacity = new Semaphore(properties.maxConcurrent());
    }

    @Scheduled(fixedDelayString = "${memehub.tagging.poll-interval:PT2S}", initialDelayString = "PT3S")
    void poll() {
        int free = capacity.availablePermits();
        if (free <= 0) {
            return;
        }
        List<UUID> claimed;
        try {
            claimed = queue.claim(free);
        } catch (RuntimeException e) {
            log.warn("Could not claim pictures to tag: {}", e.getMessage());
            return;
        }
        for (UUID id : claimed) {
            capacity.acquireUninterruptibly();
            executor.submit(() -> {
                try {
                    tagger.handle(id);
                } finally {
                    capacity.release();
                }
            });
        }
    }

    @Scheduled(fixedDelayString = "${memehub.tagging.recovery-interval:PT1M}", initialDelayString = "PT1M")
    void recoverAbandoned() {
        try {
            int touched = queue.recoverStale(properties.timeout(), properties.maxAttempts());
            if (touched > 0) {
                log.warn("Recovered {} abandoned tagging task(s)", touched);
            }
        } catch (RuntimeException e) {
            log.warn("Could not recover abandoned tagging tasks: {}", e.getMessage());
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

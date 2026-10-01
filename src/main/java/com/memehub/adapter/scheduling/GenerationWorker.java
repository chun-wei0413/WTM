package com.memehub.adapter.scheduling;

import com.memehub.application.generation.GenerationQueueHandler;
import com.memehub.application.generation.RunGenerationHandler;
import com.memehub.application.port.out.GenerationJobStore.ClaimedJob;
import jakarta.annotation.PreDestroy;
import java.util.List;
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
 * Pulls generation jobs off the queue and runs them on virtual threads, never more
 * than {@code maxConcurrentJobs} at once. Jobs spend nearly all their time waiting
 * for the model, which is exactly what virtual threads are cheap at.
 */
@Component
@ConditionalOnProperty(name = "memehub.generation.worker-enabled", havingValue = "true", matchIfMissing = true)
class GenerationWorker {

    private static final Logger log = LoggerFactory.getLogger(GenerationWorker.class);

    private final GenerationQueueHandler queue;
    private final RunGenerationHandler runner;
    private final GenerationProperties properties;
    private final Semaphore capacity;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    GenerationWorker(GenerationQueueHandler queue, RunGenerationHandler runner, GenerationProperties properties) {
        this.queue = queue;
        this.runner = runner;
        this.properties = properties;
        this.capacity = new Semaphore(properties.maxConcurrentJobs());
    }

    @Scheduled(fixedDelayString = "${memehub.generation.poll-interval:PT0.5S}", initialDelayString = "PT2S")
    void poll() {
        int free = capacity.availablePermits();
        if (free <= 0) {
            return;
        }
        List<ClaimedJob> jobs;
        try {
            jobs = queue.claim(free);
        } catch (RuntimeException e) {
            log.warn("Could not claim generation jobs: {}", e.getMessage());
            return;
        }
        for (ClaimedJob job : jobs) {
            capacity.acquireUninterruptibly();
            executor.submit(() -> {
                try {
                    runner.handle(job);
                } finally {
                    capacity.release();
                }
            });
        }
    }

    @Scheduled(fixedDelayString = "${memehub.generation.recovery-interval:PT30S}", initialDelayString = "PT30S")
    void recoverAbandonedJobs() {
        try {
            int touched = queue.recoverStale(properties.jobTimeout(), properties.maxAttempts());
            if (touched > 0) {
                log.warn("Recovered {} abandoned generation job(s)", touched);
            }
        } catch (RuntimeException e) {
            log.warn("Could not recover abandoned generation jobs: {}", e.getMessage());
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

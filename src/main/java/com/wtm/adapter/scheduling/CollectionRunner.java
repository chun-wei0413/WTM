package com.wtm.adapter.scheduling;

import com.wtm.application.collection.RunCollectionHandler;
import com.wtm.application.collection.StartCollectionHandler;
import com.wtm.application.collection.StartCollectionHandler.Plan;
import com.wtm.application.port.out.CollectionRunPort;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Starts "collect from this source" runs in the background, so the request that asked for one can
 * return at once. Only one run goes at a time (the start step refuses a second).
 */
@Component
public class CollectionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CollectionRunner.class);

    private final StartCollectionHandler start;
    private final RunCollectionHandler run;
    private final CollectionRunPort runs;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    CollectionRunner(StartCollectionHandler start, RunCollectionHandler run, CollectionRunPort runs) {
        this.start = start;
        this.run = run;
        this.runs = runs;
    }

    /** @return the id of the run, which can be followed through the run list */
    public UUID submit(String sourceId, int limit, Map<String, String> options) {
        Plan plan = start.handle(sourceId, limit, options);
        executor.submit(() -> {
            try {
                run.handle(plan);
            } catch (RuntimeException e) {
                log.error("Collection {} failed", plan.runId(), e);
                runs.finish(plan.runId(), false, "Failed: " + e.getMessage());
            }
        });
        return plan.runId();
    }

    /** Runs that were going when the application last stopped can never finish now. */
    @Override
    public void run(ApplicationArguments args) {
        int interrupted = runs.failInterrupted();
        if (interrupted > 0) {
            log.warn("Marked {} collection run(s) as interrupted", interrupted);
        }
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}

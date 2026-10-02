package com.memehub.application.collection;

import com.memehub.application.collection.StartCollectionHandler.Plan;
import com.memehub.application.port.out.CollectionRunPort;
import com.memehub.application.port.out.CollectionRunPort.Counts;
import com.memehub.application.port.out.MemeSourcePort.RemoteMeme;
import com.memehub.application.port.out.RemoteFetchPort;
import com.memehub.application.port.out.RemoteFetchPort.FetchedImage;
import java.util.Iterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects pictures from a source: goes through what it offers, downloads each picture and hands
 * it to the library. One picture failing never stops the run, and progress is saved after each one.
 */
public class RunCollectionHandler {

    private static final Logger log = LoggerFactory.getLogger(RunCollectionHandler.class);

    private final RemoteFetchPort fetcher;
    private final IngestMemeHandler ingest;
    private final CollectionRunPort runs;

    public RunCollectionHandler(RemoteFetchPort fetcher, IngestMemeHandler ingest, CollectionRunPort runs) {
        this.fetcher = fetcher;
        this.ingest = ingest;
        this.runs = runs;
    }

    public void handle(Plan plan) {
        int found = 0;
        int imported = 0;
        int duplicates = 0;
        int rejected = 0;
        int failed = 0;
        int refusedByRules = 0;
        String stopReason = null;
        try {
            Iterator<RemoteMeme> items = plan.source().discover(plan.options());
            while (found < plan.limit() && items.hasNext()) {
                RemoteMeme item = items.next();
                found++;
                try {
                    FetchedImage image = fetcher.fetchImage(item.imageUrl());
                    IngestOutcome outcome = ingest.handle(image.content(), item.title(), originOf(plan, item));
                    switch (outcome.status()) {
                        case IMPORTED -> imported++;
                        case DUPLICATE -> duplicates++;
                        case REJECTED -> rejected++;
                    }
                } catch (FetchRefusedException e) {
                    refusedByRules++;
                    rejected++;
                    log.info("Collection {}: {} not downloaded: {}", plan.runId(), item.imageUrl(), e.getMessage());
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("Collection {}: {} failed: {}", plan.runId(), item.imageUrl(), e.getMessage());
                }
                runs.update(plan.runId(), new Counts(found, imported, duplicates, rejected, failed));
            }
        } catch (RuntimeException e) {
            stopReason = e.getMessage();
            log.warn("Collection {} stopped: {}", plan.runId(), e.getMessage());
        }
        runs.update(plan.runId(), new Counts(found, imported, duplicates, rejected, failed));

        String summary = summary(found, imported, duplicates, rejected, failed, refusedByRules);
        if (stopReason != null) {
            runs.finish(plan.runId(), false, "Stopped early: " + stopReason + ". " + summary);
        } else {
            runs.finish(plan.runId(), true, summary);
        }
    }

    private static Origin originOf(Plan plan, RemoteMeme item) {
        return new Origin(plan.source().id(), item.imageUrl(), item.pageUrl(), item.attribution(), item.licenseNote());
    }

    private static String summary(int found, int imported, int duplicates, int rejected, int failed, int refusedByRules) {
        StringBuilder sb = new StringBuilder();
        sb.append("Looked at ").append(found).append(": ")
                .append(imported).append(" added, ")
                .append(duplicates).append(" already collected, ")
                .append(rejected).append(" not usable, ")
                .append(failed).append(" failed");
        if (refusedByRules > 0) {
            sb.append(" (").append(refusedByRules).append(" were not downloaded because the site's rules or limits forbid it)");
        }
        return sb.append('.').toString();
    }
}

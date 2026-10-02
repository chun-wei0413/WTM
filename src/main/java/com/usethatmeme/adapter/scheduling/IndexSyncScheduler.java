package com.usethatmeme.adapter.scheduling;

import com.usethatmeme.application.template.index.SyncSearchIndexHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically reconciles the search index with the templates.
 */
@Component
@ConditionalOnProperty(name = "usethatmeme.index.scheduler-enabled", havingValue = "true", matchIfMissing = true)
class IndexSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(IndexSyncScheduler.class);

    private final SyncSearchIndexHandler handler;
    private final IndexProperties properties;

    IndexSyncScheduler(SyncSearchIndexHandler handler, IndexProperties properties) {
        this.handler = handler;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${usethatmeme.index.sync-interval:PT15S}", initialDelayString = "PT10S")
    void sync() {
        try {
            var result = handler.handle(properties.batchSize());
            if (result.indexed() + result.removed() + result.failed() > 0) {
                log.info("Search index sync: {}", result);
            }
        } catch (RuntimeException e) {
            log.warn("Search index sync failed; will retry: {}", e.getMessage());
        }
    }
}

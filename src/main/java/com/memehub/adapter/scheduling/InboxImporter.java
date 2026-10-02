package com.memehub.adapter.scheduling;

import com.memehub.application.collection.IngestMemeHandler;
import java.nio.file.Path;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Watches {@code <data folder>/inbox}: pictures saved there by hand (from Dcard, a chat, anywhere)
 * are added to the library without any further step.
 */
@Component
@ConditionalOnProperty(name = "memehub.collection.inbox-enabled", havingValue = "true", matchIfMissing = true)
class InboxImporter {

    private static final Logger log = LoggerFactory.getLogger(InboxImporter.class);

    private final InboxScanner scanner;

    InboxImporter(IngestMemeHandler ingest, @Value("${memehub.data-dir:}") String dataDir) {
        if (dataDir == null || dataDir.isBlank()) {
            this.scanner = null;
            log.info("No data folder is configured (MEMEHUB_DATA_DIR), so the inbox is not watched");
        } else {
            Path inbox = Path.of(dataDir).resolve("inbox");
            this.scanner = new InboxScanner(inbox, ingest, Clock.systemUTC());
            log.info("Watching the inbox {}", inbox);
        }
    }

    @Scheduled(fixedDelayString = "${memehub.collection.inbox-interval:PT20S}", initialDelayString = "PT15S")
    void scan() {
        if (scanner == null) {
            return;
        }
        try {
            var counts = scanner.scan();
            if (counts.added() + counts.duplicates() + counts.rejected() + counts.failed() > 0) {
                log.info("Inbox: {}", counts);
            }
        } catch (RuntimeException e) {
            log.warn("Inbox scan failed; will retry: {}", e.getMessage());
        }
    }
}

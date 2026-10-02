package com.usethatmeme.adapter.scheduling;

import com.usethatmeme.application.collection.IngestMemeHandler;
import com.usethatmeme.application.collection.IngestOutcome;
import com.usethatmeme.application.collection.Origin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects the pictures dropped into a folder. Each file is added to the library and then moved
 * into {@code _done} (added, or already there), {@code _rejected} (not a usable picture) or
 * {@code _failed} (something went wrong), so the inbox always shows what is still waiting.
 */
class InboxScanner {

    private static final Logger log = LoggerFactory.getLogger(InboxScanner.class);

    private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif");
    private static final long MAX_BYTES = 30L * 1024 * 1024;
    /** A file touched more recently than this may still be being copied in. */
    private static final Duration SETTLE_TIME = Duration.ofSeconds(5);

    record Counts(int added, int duplicates, int rejected, int failed) {
    }

    private final Path inbox;
    private final IngestMemeHandler ingest;
    private final Clock clock;

    InboxScanner(Path inbox, IngestMemeHandler ingest, Clock clock) {
        this.inbox = inbox;
        this.ingest = ingest;
        this.clock = clock;
    }

    Counts scan() {
        int added = 0;
        int duplicates = 0;
        int rejected = 0;
        int failed = 0;
        for (Path file : waitingFiles()) {
            try {
                IngestOutcome outcome = ingest.handle(Files.readAllBytes(file), file.getFileName().toString(),
                        new Origin("INBOX", null, null, null, null));
                switch (outcome.status()) {
                    case IMPORTED -> {
                        added++;
                        moveTo(file, "_done");
                    }
                    case DUPLICATE -> {
                        duplicates++;
                        moveTo(file, "_done");
                    }
                    case REJECTED -> {
                        rejected++;
                        moveTo(file, "_rejected");
                    }
                }
            } catch (IOException | RuntimeException e) {
                failed++;
                log.warn("Inbox file {} could not be added: {}", file.getFileName(), e.getMessage());
                try {
                    moveTo(file, "_failed");
                } catch (IOException moveError) {
                    log.warn("Inbox file {} could not be moved aside: {}", file.getFileName(), moveError.getMessage());
                }
            }
        }
        return new Counts(added, duplicates, rejected, failed);
    }

    private List<Path> waitingFiles() {
        if (!Files.isDirectory(inbox)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(inbox)) {
            return entries.filter(Files::isRegularFile)
                    .filter(p -> EXTENSIONS.contains(extensionOf(p)))
                    .filter(this::isSettled)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.warn("Could not read the inbox {}: {}", inbox, e.getMessage());
            return List.of();
        }
    }

    private boolean isSettled(Path file) {
        try {
            if (Files.size(file) > MAX_BYTES) {
                return false;
            }
            return clock.instant().minus(SETTLE_TIME).isAfter(Files.getLastModifiedTime(file).toInstant());
        } catch (IOException e) {
            return false;
        }
    }

    private void moveTo(Path file, String folder) throws IOException {
        Path target = inbox.resolve(folder);
        Files.createDirectories(target);
        Path destination = target.resolve(file.getFileName());
        if (Files.exists(destination)) {
            destination = target.resolve(clock.millis() + "-" + file.getFileName());
        }
        Files.move(file, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String extensionOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

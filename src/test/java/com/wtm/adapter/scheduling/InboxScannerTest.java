package com.wtm.adapter.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtm.application.collection.IngestMemeHandler;
import com.wtm.application.collection.IngestOutcome;
import com.wtm.application.collection.Origin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class InboxScannerTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    @TempDir Path inbox;

    private final IngestMemeHandler ingest = mock(IngestMemeHandler.class);
    private InboxScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new InboxScanner(inbox, ingest, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Path drop(String name, String content, long secondsOld) throws IOException {
        Path file = inbox.resolve(name);
        Files.writeString(file, content);
        Files.setLastModifiedTime(file, FileTime.from(NOW.minusSeconds(secondsOld)));
        return file;
    }

    @Test
    void addsWaitingPicturesAndFilesThemAccordingToWhatHappened() throws IOException {
        drop("new.png", "n", 60);
        drop("again.jpg", "a", 60);
        drop("broken.gif", "b", 60);
        when(ingest.handle(any(), eq("new.png"), any())).thenReturn(IngestOutcome.imported(UUID.randomUUID()));
        when(ingest.handle(any(), eq("again.jpg"), any())).thenReturn(IngestOutcome.duplicate(UUID.randomUUID()));
        when(ingest.handle(any(), eq("broken.gif"), any())).thenReturn(IngestOutcome.rejected("unreadable"));

        var counts = scanner.scan();

        assertThat(counts).isEqualTo(new InboxScanner.Counts(1, 1, 1, 0));
        assertThat(inbox.resolve("_done/new.png")).exists();
        assertThat(inbox.resolve("_done/again.jpg")).exists();
        assertThat(inbox.resolve("_rejected/broken.gif")).exists();
        assertThat(inbox.resolve("new.png")).doesNotExist();
    }

    @Test
    void tellsTheLibraryWhereThePictureCameFrom() throws IOException {
        drop("meme.png", "x", 60);
        when(ingest.handle(any(), any(), any())).thenReturn(IngestOutcome.imported(UUID.randomUUID()));

        scanner.scan();

        ArgumentCaptor<Origin> origin = ArgumentCaptor.forClass(Origin.class);
        verify(ingest).handle(any(), eq("meme.png"), origin.capture());
        assertThat(origin.getValue().sourceType()).isEqualTo("INBOX");
    }

    @Test
    void leavesAFileAloneThatMayStillBeBeingCopiedIn() throws IOException {
        drop("copying.png", "x", 1);

        var counts = scanner.scan();

        assertThat(counts).isEqualTo(new InboxScanner.Counts(0, 0, 0, 0));
        assertThat(inbox.resolve("copying.png")).exists();
        verify(ingest, never()).handle(any(), any(), any());
    }

    @Test
    void ignoresFilesThatAreNotPicturesAndFoldersItManages() throws IOException {
        drop("notes.txt", "x", 60);
        drop("doc.pdf", "x", 60);
        Files.createDirectories(inbox.resolve("_done"));
        Files.writeString(inbox.resolve("_done/old.png"), "x");

        scanner.scan();

        verify(ingest, never()).handle(any(), any(), any());
        assertThat(inbox.resolve("notes.txt")).exists();
    }

    @Test
    void aFileThatFailsIsSetAsideSoItIsNotTriedAgainForever() throws IOException {
        drop("bad.png", "x", 60);
        drop("good.png", "y", 60);
        when(ingest.handle(any(), eq("bad.png"), any())).thenThrow(new IllegalStateException("db down"));
        when(ingest.handle(any(), eq("good.png"), any())).thenReturn(IngestOutcome.imported(UUID.randomUUID()));

        var counts = scanner.scan();

        assertThat(counts).isEqualTo(new InboxScanner.Counts(1, 0, 0, 1));
        assertThat(inbox.resolve("_failed/bad.png")).exists();
        assertThat(inbox.resolve("_done/good.png")).exists();
    }

    @Test
    void doesNotOverwriteAnEarlierFileWithTheSameName() throws IOException {
        Files.createDirectories(inbox.resolve("_done"));
        Files.writeString(inbox.resolve("_done/same.png"), "older");
        drop("same.png", "newer", 60);
        when(ingest.handle(any(), any(), any())).thenReturn(IngestOutcome.imported(UUID.randomUUID()));

        scanner.scan();

        assertThat(Files.readString(inbox.resolve("_done/same.png"))).isEqualTo("older");
        try (var done = Files.list(inbox.resolve("_done"))) {
            assertThat(done).hasSize(2);
        }
    }

    @Test
    void aMissingInboxIsNotAnError() {
        var missing = new InboxScanner(inbox.resolve("nope"), ingest, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(missing.scan()).isEqualTo(new InboxScanner.Counts(0, 0, 0, 0));
    }

    @Test
    void whenLookedAtRightAfterItWasWrittenTheFileCountsAsStillSettling() throws IOException {
        Path file = drop("fresh.png", "x", 0);
        Files.setLastModifiedTime(file, FileTime.from(NOW));
        when(ingest.handle(any(), any(), any())).thenReturn(IngestOutcome.imported(UUID.randomUUID()));

        assertThat(scanner.scan().added()).isZero();
    }
}

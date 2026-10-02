package com.wtm.application.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtm.application.collection.StartCollectionHandler.Plan;
import com.wtm.application.port.out.CollectionRunPort;
import com.wtm.application.port.out.CollectionRunPort.Counts;
import com.wtm.application.port.out.MemeSourcePort;
import com.wtm.application.port.out.MemeSourcePort.RemoteMeme;
import com.wtm.application.port.out.RemoteFetchPort;
import com.wtm.application.port.out.RemoteFetchPort.FetchedImage;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CollectionHandlersTest {

    private final CollectionRunPort runs = mock(CollectionRunPort.class);
    private final MemeSourcePort source = mock(MemeSourcePort.class);
    private final RemoteFetchPort fetcher = mock(RemoteFetchPort.class);
    private final IngestMemeHandler ingest = mock(IngestMemeHandler.class);

    private RemoteMeme item(int n) {
        return new RemoteMeme("https://img.example/" + n + ".jpg", "https://page/" + n, "name " + n, "by " + n, "lic");
    }

    private Plan plan(int limit) {
        return new Plan(UUID.randomUUID(), source, limit, Map.of());
    }

    private static FetchedImage image() {
        return new FetchedImage(new byte[] {1}, "image/jpeg");
    }

    // ---- StartCollectionHandler ------------------------------------------------------------------

    private StartCollectionHandler start() {
        when(source.id()).thenReturn("IMGFLIP");
        return new StartCollectionHandler(List.of(source), runs);
    }

    @Test
    void recordsARunForAKnownSource() {
        Plan plan = start().handle("imgflip", 20, Map.of("b", "2", "a", "1"));

        assertThat(plan.source()).isSameAs(source);
        assertThat(plan.limit()).isEqualTo(20);
        verify(runs).create(plan.runId(), "IMGFLIP", "{a=1, b=2}");
    }

    @Test
    void refusesAnUnknownSourceAndALimitOutOfRange() {
        StartCollectionHandler start = start();

        assertThatThrownBy(() -> start.handle("NOPE", 10, Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> start.handle(null, 10, Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> start.handle("IMGFLIP", 0, Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> start.handle("IMGFLIP", StartCollectionHandler.MAX_LIMIT + 1, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(runs, never()).create(any(), anyString(), anyString());
    }

    @Test
    void onlyOneCollectionRunsAtATime() {
        when(runs.anyRunning()).thenReturn(true);

        assertThatThrownBy(() -> start().handle("IMGFLIP", 10, Map.of())).isInstanceOf(CollectionBusyException.class);
        verify(runs, never()).create(any(), anyString(), anyString());
    }

    // ---- RunCollectionHandler --------------------------------------------------------------------

    private RunCollectionHandler runner() {
        return new RunCollectionHandler(fetcher, ingest, runs);
    }

    @Test
    void goesThroughTheItemsAndCountsWhatHappenedToEach() {
        Iterator<RemoteMeme> items = List.of(item(1), item(2), item(3), item(4)).iterator();
        when(source.discover(any())).thenReturn(items);
        when(source.id()).thenReturn("PTT");
        when(fetcher.fetchImage(anyString())).thenReturn(image());
        when(ingest.handle(any(), eq("name 1"), any())).thenReturn(IngestOutcome.imported(UUID.randomUUID()));
        when(ingest.handle(any(), eq("name 2"), any())).thenReturn(IngestOutcome.duplicate(UUID.randomUUID()));
        when(ingest.handle(any(), eq("name 3"), any())).thenReturn(IngestOutcome.rejected("too small"));
        when(ingest.handle(any(), eq("name 4"), any())).thenThrow(new IllegalStateException("db down"));
        Plan plan = plan(10);

        runner().handle(plan);

        verify(runs, org.mockito.Mockito.atLeastOnce()).update(plan.runId(), new Counts(4, 1, 1, 1, 1));
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(runs).finish(eq(plan.runId()), eq(true), message.capture());
        assertThat(message.getValue()).contains("Looked at 4", "1 added", "1 already collected", "1 not usable", "1 failed");
    }

    @Test
    void passesTheOriginOfEachPictureToTheLibrary() {
        when(source.discover(any())).thenReturn(List.of(item(7)).iterator());
        when(source.id()).thenReturn("WIKIMEDIA");
        when(fetcher.fetchImage(anyString())).thenReturn(image());
        when(ingest.handle(any(), any(), any())).thenReturn(IngestOutcome.imported(UUID.randomUUID()));

        runner().handle(plan(10));

        ArgumentCaptor<Origin> origin = ArgumentCaptor.forClass(Origin.class);
        verify(ingest).handle(any(), eq("name 7"), origin.capture());
        assertThat(origin.getValue()).isEqualTo(new Origin("WIKIMEDIA", "https://img.example/7.jpg", "https://page/7", "by 7", "lic"));
    }

    @Test
    void stopsAtTheLimitWithoutAskingTheSourceForMore() {
        Iterator<RemoteMeme> items = mock(Iterator.class);
        when(items.hasNext()).thenReturn(true);
        when(items.next()).thenReturn(item(1));
        when(source.discover(any())).thenReturn(items);
        when(source.id()).thenReturn("X");
        when(fetcher.fetchImage(anyString())).thenReturn(image());
        when(ingest.handle(any(), any(), any())).thenReturn(IngestOutcome.imported(UUID.randomUUID()));
        Plan plan = plan(3);

        runner().handle(plan);

        verify(items, org.mockito.Mockito.times(3)).next();
        verify(runs, org.mockito.Mockito.atLeastOnce()).update(plan.runId(), new Counts(3, 3, 0, 0, 0));
    }

    @Test
    void aPictureTheSiteDoesNotAllowCountsAsNotUsableAndTheRunGoesOn() {
        when(source.discover(any())).thenReturn(List.of(item(1), item(2)).iterator());
        when(source.id()).thenReturn("X");
        when(fetcher.fetchImage("https://img.example/1.jpg")).thenThrow(new FetchRefusedException("robots.txt"));
        when(fetcher.fetchImage("https://img.example/2.jpg")).thenReturn(image());
        when(ingest.handle(any(), any(), any())).thenReturn(IngestOutcome.imported(UUID.randomUUID()));
        Plan plan = plan(10);

        runner().handle(plan);

        verify(runs, org.mockito.Mockito.atLeastOnce()).update(plan.runId(), new Counts(2, 1, 0, 1, 0));
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(runs).finish(eq(plan.runId()), eq(true), message.capture());
        assertThat(message.getValue()).contains("rules or limits");
    }

    @Test
    void whenTheSourceBreaksTheRunEndsAsFailedButKeepsWhatItAlreadyGathered() {
        Iterator<RemoteMeme> items = new Iterator<>() {
            private int n;

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public RemoteMeme next() {
                if (++n > 1) {
                    throw new FetchRefusedException("The board asks for an age confirmation");
                }
                return item(1);
            }
        };
        when(source.discover(any())).thenReturn(items);
        when(source.id()).thenReturn("X");
        when(fetcher.fetchImage(anyString())).thenReturn(image());
        when(ingest.handle(any(), any(), any())).thenReturn(IngestOutcome.imported(UUID.randomUUID()));
        Plan plan = plan(10);

        runner().handle(plan);

        verify(runs, org.mockito.Mockito.atLeastOnce()).update(plan.runId(), new Counts(1, 1, 0, 0, 0));
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(runs).finish(eq(plan.runId()), eq(false), message.capture());
        assertThat(message.getValue()).contains("age confirmation").contains("1 added");
    }

    @Test
    void aSourceThatCannotEvenStartFailsTheRun() {
        when(source.discover(any())).thenThrow(new FetchRefusedException("could not reach the site"));
        Plan plan = plan(10);

        runner().handle(plan);

        verify(runs).finish(eq(plan.runId()), eq(false), anyString());
    }

    // ---- ImportFromUrlHandler ---------------------------------------------------------------------

    @Test
    void addsOnePictureFromItsAddress() {
        when(fetcher.fetchImage("https://img.example/x.jpg")).thenReturn(image());
        UUID id = UUID.randomUUID();
        when(ingest.handle(any(), eq("my title"), any())).thenReturn(IngestOutcome.imported(id));

        IngestOutcome outcome = new ImportFromUrlHandler(fetcher, ingest)
                .handle(" https://img.example/x.jpg ", " my title ", "https://page");

        assertThat(outcome.templateId()).isEqualTo(id);
        ArgumentCaptor<Origin> origin = ArgumentCaptor.forClass(Origin.class);
        verify(ingest).handle(any(), eq("my title"), origin.capture());
        assertThat(origin.getValue()).isEqualTo(new Origin("URL", "https://img.example/x.jpg", "https://page", null, null));
    }

    @Test
    void anEmptyAddressIsRejectedAndABlankTitleBecomesNoTitle() {
        ImportFromUrlHandler handler = new ImportFromUrlHandler(fetcher, ingest);
        assertThatThrownBy(() -> handler.handle("  ", null, null)).isInstanceOf(IllegalArgumentException.class);

        when(fetcher.fetchImage(anyString())).thenReturn(image());
        when(ingest.handle(any(), any(), any())).thenReturn(IngestOutcome.imported(UUID.randomUUID()));
        handler.handle("https://img.example/x.jpg", "  ", "");

        verify(ingest).handle(any(), eq(null), any());
    }
}

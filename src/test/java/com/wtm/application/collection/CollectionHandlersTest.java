package com.wtm.application.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtm.application.port.out.RemoteFetchPort;
import com.wtm.application.port.out.RemoteFetchPort.FetchedImage;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CollectionHandlersTest {

    private final RemoteFetchPort fetcher = mock(RemoteFetchPort.class);
    private final IngestMemeHandler ingest = mock(IngestMemeHandler.class);

    private static FetchedImage image() {
        return new FetchedImage(new byte[] {1}, "image/jpeg");
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

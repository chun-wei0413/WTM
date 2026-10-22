package com.wtm.curation.usecase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtm.curation.entity.Origin;
import com.wtm.curation.usecase.port.in.command.ImportFromUrlUseCase;
import com.wtm.curation.usecase.port.in.command.IngestMemeUseCase;
import com.wtm.curation.usecase.port.in.command.IngestOutcome;
import com.wtm.curation.usecase.port.out.fetch.RemoteFetchPort;
import com.wtm.curation.usecase.port.out.fetch.RemoteFetchPort.FetchedImage;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CollectionServicesTest {

    private final RemoteFetchPort fetcher = mock(RemoteFetchPort.class);
    private final IngestMemeService ingest = mock(IngestMemeService.class);

    private static FetchedImage image() {
        return new FetchedImage(new byte[] {1}, "image/jpeg");
    }

    // ---- ImportFromUrlService ---------------------------------------------------------------------

    @Test
    void addsOnePictureFromItsAddress() {
        when(fetcher.fetchImage("https://img.example/x.jpg")).thenReturn(image());
        UUID id = UUID.randomUUID();
        when(ingest.handle(org.mockito.ArgumentMatchers.argThat(in -> in != null && java.util.Objects.equals(in.suggestedName(), "my title")))).thenReturn(IngestOutcome.imported(id));

        IngestOutcome outcome = new ImportFromUrlService(fetcher, ingest)
                .handle(new ImportFromUrlUseCase.Input(" https://img.example/x.jpg ", " my title ", "https://page"));

        assertThat(outcome.templateId()).isEqualTo(id);
        ArgumentCaptor<IngestMemeUseCase.Input> input = ArgumentCaptor.forClass(IngestMemeUseCase.Input.class);
        verify(ingest).handle(input.capture());
        assertThat(input.getValue().suggestedName()).isEqualTo("my title");
        assertThat(input.getValue().origin()).isEqualTo(new Origin("URL", "https://img.example/x.jpg", "https://page", null, null));
    }

    @Test
    void anEmptyAddressIsRejectedAndABlankTitleBecomesNoTitle() {
        ImportFromUrlService handler = new ImportFromUrlService(fetcher, ingest);
        assertThatThrownBy(() -> handler.handle(new ImportFromUrlUseCase.Input("  ", null, null))).isInstanceOf(IllegalArgumentException.class);

        when(fetcher.fetchImage(anyString())).thenReturn(image());
        when(ingest.handle(any(IngestMemeUseCase.Input.class))).thenReturn(IngestOutcome.imported(UUID.randomUUID()));
        handler.handle(new ImportFromUrlUseCase.Input("https://img.example/x.jpg", "  ", ""));

        verify(ingest).handle(org.mockito.ArgumentMatchers.argThat(in -> in != null && java.util.Objects.equals(in.suggestedName(), null)));
    }
}

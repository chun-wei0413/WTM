package com.usethatmeme.application.template.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.usethatmeme.application.port.out.EmbeddingPort;
import com.usethatmeme.application.port.out.LlmUnavailableException;
import com.usethatmeme.application.port.out.SearchIndexPort;
import com.usethatmeme.application.port.out.SearchIndexPort.IndexableTemplate;
import com.usethatmeme.domain.template.MemeProfile;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SyncSearchIndexHandlerTest {

    private final SearchIndexPort index = mock(SearchIndexPort.class);
    private final EmbeddingPort embeddings = mock(EmbeddingPort.class);
    private final SyncSearchIndexHandler handler = new SyncSearchIndexHandler(index, embeddings);

    private static IndexableTemplate template(UUID id, String status) {
        return new IndexableTemplate(id, "Drake", status,
                new MemeProfile("meaning", List.of("usage"), List.of("smug"), List.of("alias")),
                List.of(), Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void indexesTemplatesThatNeedIt() {
        UUID id = UUID.randomUUID();
        float[] vector = {0.1f, 0.2f};
        when(index.findIndexEntriesToRemove(50)).thenReturn(List.of());
        when(index.findTemplatesNeedingIndex(50)).thenReturn(List.of(id));
        when(index.load(id)).thenReturn(Optional.of(template(id, "APPROVED")));
        when(embeddings.embed(any())).thenReturn(vector);

        var result = handler.handle(50);

        assertThat(result).isEqualTo(new SyncSearchIndexHandler.Result(1, 0, 0));
        verify(index).upsert(any(), any(), any(), any(), any());
    }

    @Test
    void removesEntriesOfTemplatesThatAreNoLongerApproved() {
        UUID id = UUID.randomUUID();
        when(index.findIndexEntriesToRemove(50)).thenReturn(List.of(id));
        when(index.findTemplatesNeedingIndex(50)).thenReturn(List.of());

        var result = handler.handle(50);

        assertThat(result.removed()).isEqualTo(1);
        verify(index).remove(id);
    }

    @Test
    void aFailingTemplateDoesNotStopTheOthersAndIsRetriedLater() {
        UUID bad = UUID.randomUUID();
        UUID good = UUID.randomUUID();
        when(index.findIndexEntriesToRemove(50)).thenReturn(List.of());
        when(index.findTemplatesNeedingIndex(50)).thenReturn(List.of(bad, good));
        when(index.load(bad)).thenReturn(Optional.of(template(bad, "APPROVED")));
        when(index.load(good)).thenReturn(Optional.of(template(good, "APPROVED")));
        when(embeddings.embed(any()))
                .thenThrow(new LlmUnavailableException("embedding service down"))
                .thenReturn(new float[] {1f});

        var result = handler.handle(50);

        assertThat(result).isEqualTo(new SyncSearchIndexHandler.Result(1, 0, 1));
        verify(index).upsert(any(), any(), any(), any(), any());
    }

    @Test
    void skipsTemplateThatStoppedBeingApprovedMeanwhile() {
        UUID id = UUID.randomUUID();
        when(index.findIndexEntriesToRemove(50)).thenReturn(List.of());
        when(index.findTemplatesNeedingIndex(50)).thenReturn(List.of(id));
        when(index.load(id)).thenReturn(Optional.of(template(id, "RETIRED")));

        var result = handler.handle(50);

        assertThat(result.indexed()).isZero();
        verify(index, never()).upsert(any(), any(), any(), any(), any());
    }

    @Test
    void searchTextContainsEverythingThatDescribesTheTemplate() {
        String text = SearchText.of(template(UUID.randomUUID(), "APPROVED"));

        assertThat(text).contains("Drake", "alias", "meaning", "usage", "smug");
    }
}

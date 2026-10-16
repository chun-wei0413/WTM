package com.wtm.discovery.usecase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtm.discovery.entity.Hit;
import com.wtm.discovery.usecase.port.in.query.PickResult;
import com.wtm.discovery.usecase.port.in.query.SearchResult;
import com.wtm.discovery.usecase.port.out.embedding.EmbeddingPort;
import com.wtm.discovery.usecase.port.out.explainer.MemeExplainerPort;
import com.wtm.discovery.usecase.port.out.explainer.MemeExplainerPort.Meme;
import com.wtm.discovery.usecase.port.out.search.TemplateSearchPort;
import com.wtm.discovery.usecase.port.out.search.TemplateSearchPort.SearchCard;
import com.wtm.shared.RateLimiterPort;
import com.wtm.shared.TooManyRequestsException;
import com.wtm.shared.port.LlmUnavailableException;
import com.wtm.shared.port.ObjectStoragePort;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PickMemeServiceTest {

    private final TemplateSearchPort searchPort = mock(TemplateSearchPort.class);
    private final EmbeddingPort embeddings = mock(EmbeddingPort.class);
    private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
    private final MemeExplainerPort explainer = mock(MemeExplainerPort.class);
    private final RateLimiterPort limiter = mock(RateLimiterPort.class);
    private final PickMemeService handler =
            new PickMemeService(new SearchTemplatesService(searchPort, (query, limit) -> List.of(), embeddings, storage,
                    SearchTemplatesService.DEFAULT_DENSE_WEIGHT), explainer, limiter);

    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID third = UUID.randomUUID();

    private void libraryFinds(UUID... ids) {
        when(embeddings.embed(anyString())).thenReturn(new float[] {0.1f});
        // the closest first: the first id is the most similar
        List<Hit> hits = new java.util.ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            hits.add(new Hit(ids[i], 1.0 - i * 0.1));
        }
        when(searchPort.byVector(any(), anyInt())).thenReturn(hits);
        when(searchPort.searchTexts()).thenReturn(List.of());
        when(searchPort.searchTextsVersion()).thenReturn("empty");
        when(searchPort.cards(any())).thenAnswer(call -> {
            var requested = call.<java.util.Collection<UUID>>getArgument(0);
            return requested.stream().map(PickMemeServiceTest::card).toList();
        });
        when(storage.presignedGetUrl(anyString(), any())).thenReturn("http://images/x");
        when(limiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
    }

    private static SearchCard card(UUID id) {
        return new SearchCard(id, "name-" + id, "key-" + id, 100, 100, List.of(), "meaning-" + id,
                List.of("usage-" + id), List.of("emotion"), List.of("tag"), "text", "UPLOAD", null, null, null);
    }

    @Test
    void theClosestResultIsThePickAndTheRestKeepTheirOrder() {
        libraryFinds(first, second, third);
        when(explainer.explain(anyString(), any())).thenReturn("  because it fits  ");

        PickResult result = handler.handle("user-1", "my friend says I overreact");

        assertThat(result.chosen().templateId()).isEqualTo(first);
        assertThat(result.reason()).isEqualTo("because it fits");
        assertThat(result.others()).extracting(SearchResult::templateId).containsExactly(second, third);
    }

    @Test
    void theModelIsOnlyShownTheClosestMemesDescription() {
        libraryFinds(first, second);
        when(explainer.explain(anyString(), any())).thenReturn("ok");

        handler.handle("user-1", "a situation");

        verify(explainer).explain("a situation", new Meme("name-" + first, "meaning-" + first,
                List.of("usage-" + first), List.of("emotion"), List.of("tag"), "text"));
    }

    @Test
    void offersTheClosestResultWithoutAReasonWhenTheModelCannotBeReached() {
        libraryFinds(first, second);
        when(explainer.explain(anyString(), any())).thenThrow(new LlmUnavailableException("down"));

        PickResult result = handler.handle("user-1", "a situation");

        assertThat(result.chosen().templateId()).isEqualTo(first);
        assertThat(result.reason()).isNull();
        assertThat(result.others()).extracting(SearchResult::templateId).containsExactly(second);
    }

    @Test
    void aBlankReasonBecomesNoReason() {
        libraryFinds(first);
        when(explainer.explain(anyString(), any())).thenReturn("   ");

        assertThat(handler.handle("user-1", "a situation").reason()).isNull();
    }

    @Test
    void aLibraryWithNothingToOfferDoesNotAskTheModel() {
        libraryFinds();

        PickResult result = handler.handle("user-1", "a situation");

        assertThat(result.chosen()).isNull();
        assertThat(result.others()).isEmpty();
        verify(explainer, never()).explain(anyString(), any());
    }

    @Test
    void refusesABlankOrOverlongSituation() {
        libraryFinds(first);

        assertThatThrownBy(() -> handler.handle("user-1", "   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle("user-1", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle("user-1", "x".repeat(PickMemeService.MAX_SITUATION_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(explainer, never()).explain(anyString(), any());
    }

    @Test
    void stopsAPersonWhoAsksTooOftenBeforeAnythingIsSearched() {
        when(limiter.tryAcquire(eq("pick:user-1"), eq(PickMemeService.PICKS_PER_WINDOW), any(Duration.class)))
                .thenReturn(false);

        assertThatThrownBy(() -> handler.handle("user-1", "a situation"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(PickMemeService.WINDOW));
        verify(embeddings, never()).embed(anyString());
        verify(explainer, never()).explain(anyString(), any());
    }
}

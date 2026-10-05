package com.wtm.application.template.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtm.application.TooManyRequestsException;
import com.wtm.application.port.out.EmbeddingPort;
import com.wtm.application.port.out.LlmUnavailableException;
import com.wtm.application.port.out.MemePickerPort;
import com.wtm.application.port.out.MemePickerPort.Candidate;
import com.wtm.application.port.out.MemePickerPort.Pick;
import com.wtm.application.port.out.ObjectStoragePort;
import com.wtm.application.port.out.RateLimiterPort;
import com.wtm.application.port.out.TemplateSearchPort;
import com.wtm.application.port.out.TemplateSearchPort.SearchCard;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PickMemeHandlerTest {

    private final TemplateSearchPort searchPort = mock(TemplateSearchPort.class);
    private final EmbeddingPort embeddings = mock(EmbeddingPort.class);
    private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
    private final MemePickerPort picker = mock(MemePickerPort.class);
    private final RateLimiterPort limiter = mock(RateLimiterPort.class);
    private final PickMemeHandler handler =
            new PickMemeHandler(new SearchTemplatesHandler(searchPort, embeddings, storage), picker, limiter);

    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID third = UUID.randomUUID();

    private void libraryFinds(UUID... ids) {
        when(embeddings.embed(anyString())).thenReturn(new float[] {0.1f});
        when(searchPort.byVector(any(), anyInt())).thenReturn(List.of(ids));
        when(searchPort.byKeyword(anyString(), anyInt())).thenReturn(List.of());
        when(searchPort.cards(any())).thenAnswer(call -> {
            var requested = call.<java.util.Collection<UUID>>getArgument(0);
            return requested.stream().map(PickMemeHandlerTest::card).toList();
        });
        when(storage.presignedGetUrl(anyString(), any())).thenReturn("http://images/x");
        when(limiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
    }

    private static SearchCard card(UUID id) {
        return new SearchCard(id, "name-" + id, "key-" + id, 100, 100, List.of(), "meaning-" + id,
                List.of("usage-" + id), List.of("emotion"), List.of("tag"), "text", "UPLOAD", null, null);
    }

    @Test
    void chosenMemeComesFirstAndTheRestKeepTheirOrder() {
        libraryFinds(first, second, third);
        when(picker.pick(anyString(), anyList())).thenReturn(new Pick(1, "  because it fits  "));

        PickResult result = handler.handle("user-1", "my friend says I overreact");

        assertThat(result.chosen().templateId()).isEqualTo(second);
        assertThat(result.reason()).isEqualTo("because it fits");
        assertThat(result.others()).extracting(SearchResult::templateId).containsExactly(first, third);
    }

    @Test
    void theModelIsShownTheDescriptionsOfTheSearchResults() {
        libraryFinds(first, second);
        when(picker.pick(anyString(), anyList())).thenReturn(new Pick(0, "ok"));

        handler.handle("user-1", "a situation");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Candidate>> shown = ArgumentCaptor.forClass(List.class);
        verify(picker).pick(eq("a situation"), shown.capture());
        assertThat(shown.getValue()).hasSize(2);
        assertThat(shown.getValue().get(0)).isEqualTo(new Candidate("name-" + first, "meaning-" + first,
                List.of("usage-" + first), List.of("emotion"), List.of("tag"), "text"));
    }

    @Test
    void offersTheClosestResultWithoutAReasonWhenTheModelCannotBeReached() {
        libraryFinds(first, second);
        when(picker.pick(anyString(), anyList())).thenThrow(new LlmUnavailableException("down"));

        PickResult result = handler.handle("user-1", "a situation");

        assertThat(result.chosen().templateId()).isEqualTo(first);
        assertThat(result.reason()).isNull();
        assertThat(result.others()).extracting(SearchResult::templateId).containsExactly(second);
    }

    @Test
    void aChoiceOutsideTheListIsTreatedAsNoAnswer() {
        libraryFinds(first, second);
        when(picker.pick(anyString(), anyList())).thenReturn(new Pick(7, "made up"));

        PickResult result = handler.handle("user-1", "a situation");

        assertThat(result.chosen().templateId()).isEqualTo(first);
        assertThat(result.reason()).isNull();
    }

    @Test
    void aBlankReasonBecomesNoReason() {
        libraryFinds(first);
        when(picker.pick(anyString(), anyList())).thenReturn(new Pick(0, "   "));

        assertThat(handler.handle("user-1", "a situation").reason()).isNull();
    }

    @Test
    void aLibraryWithNothingToOfferDoesNotAskTheModel() {
        libraryFinds();

        PickResult result = handler.handle("user-1", "a situation");

        assertThat(result.chosen()).isNull();
        assertThat(result.others()).isEmpty();
        verify(picker, never()).pick(anyString(), anyList());
    }

    @Test
    void refusesABlankOrOverlongSituation() {
        libraryFinds(first);

        assertThatThrownBy(() -> handler.handle("user-1", "   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle("user-1", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle("user-1", "x".repeat(PickMemeHandler.MAX_SITUATION_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(picker, never()).pick(anyString(), anyList());
    }

    @Test
    void stopsAPersonWhoAsksTooOftenBeforeAnythingIsSearched() {
        when(limiter.tryAcquire(eq("pick:user-1"), eq(PickMemeHandler.PICKS_PER_WINDOW), any(Duration.class)))
                .thenReturn(false);

        assertThatThrownBy(() -> handler.handle("user-1", "a situation"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(PickMemeHandler.WINDOW));
        verify(embeddings, never()).embed(anyString());
        verify(picker, never()).pick(anyString(), anyList());
    }
}

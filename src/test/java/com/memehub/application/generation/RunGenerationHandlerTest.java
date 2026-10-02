package com.memehub.application.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memehub.application.port.out.GenerationJobStore;
import com.memehub.application.port.out.GenerationJobStore.ClaimedJob;
import com.memehub.application.port.out.LlmUnavailableException;
import com.memehub.application.port.out.MemeAssistantPort;
import com.memehub.application.port.out.MemeRendererPort;
import com.memehub.application.port.out.MemeRendererPort.RenderedImage;
import com.memehub.application.port.out.MemeRepository;
import com.memehub.application.port.out.ObjectStoragePort;
import com.memehub.application.port.out.TemplateReadPort;
import com.memehub.application.template.query.TemplateView;
import com.memehub.application.template.search.SearchResult;
import com.memehub.application.template.search.SearchTemplatesHandler;
import com.memehub.domain.meme.Meme;
import com.memehub.domain.template.MemeProfile;
import com.memehub.domain.template.Slot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RunGenerationHandlerTest {

    private final SearchTemplatesHandler search = mock(SearchTemplatesHandler.class);
    private final TemplateReadPort templates = mock(TemplateReadPort.class);
    private final MemeAssistantPort assistant = mock(MemeAssistantPort.class);
    private final MemeRendererPort renderer = mock(MemeRendererPort.class);
    private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
    private final MemeRepository memes = mock(MemeRepository.class);
    private final GenerationJobStore jobs = mock(GenerationJobStore.class);
    private final RunGenerationHandler handler =
            new RunGenerationHandler(search, templates, assistant, renderer, storage, memes, jobs, 3);

    private final UUID requester = UUID.randomUUID();
    private final ClaimedJob job = new ClaimedJob(UUID.randomUUID(), requester, "老闆又改需求", 1);
    private final byte[] original = {1, 2, 3};
    private final byte[] rendered = {9, 9, 9};

    @BeforeEach
    void setUp() {
        when(storage.get(anyString())).thenReturn(original);
        when(renderer.render(any(), anyList(), anyMap()))
                .thenReturn(new RenderedImage(rendered, "png", "image/png"));
    }

    private static Slot slot(int no, int maxChars) {
        return new Slot(no, "role-" + no, maxChars, true, 0, 0, 100, 50);
    }

    private TemplateView template(UUID id, String status, int version, List<Slot> slots) {
        return new TemplateView(id, "Template " + id.toString().substring(0, 4), status, version, 600, 600,
                "templates/" + id + ".png", null,
                new MemeProfile("meaning", List.of("usage"), List.of(), List.of()), slots,
                Instant.now(), Instant.now());
    }

    private TemplateView available(UUID id, List<Slot> slots) {
        TemplateView view = template(id, "APPROVED", 2, slots);
        when(templates.findById(id)).thenReturn(Optional.of(view));
        return view;
    }

    private void searchReturns(UUID... ids) {
        when(search.handle(eq(job.situation()), anyInt())).thenReturn(
                java.util.Arrays.stream(ids)
                        .map(id -> new SearchResult(id, "name", 1.0, "url", List.of(), "", List.of(), "",
                                null, null, null)).toList());
    }

    @Test
    void makesACandidateForEveryTemplateFound() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        available(a, List.of(slot(1, 10), slot(2, 10)));
        available(b, List.of(slot(1, 10)));
        searchReturns(a, b);
        when(assistant.writeCaptions(anyString(), any())).thenReturn(Map.of(1, "上", 2, "下"));

        handler.handle(job);

        ArgumentCaptor<Meme> saved = ArgumentCaptor.forClass(Meme.class);
        verify(memes, org.mockito.Mockito.times(2)).save(saved.capture());
        Meme first = saved.getAllValues().get(0);
        assertThat(first.ownerId()).isEqualTo(requester);
        assertThat(first.template().templateId()).isEqualTo(a);
        assertThat(first.template().version()).isEqualTo(2);
        assertThat(first.captions()).containsEntry(1, "上").containsEntry(2, "下");
        assertThat(first.imageKey()).startsWith("memes/").endsWith(".png");

        verify(storage, org.mockito.Mockito.times(2)).put(startsWith("memes/"), eq(rendered), eq("image/png"));
        ArgumentCaptor<List<UUID>> ids = ArgumentCaptor.forClass(List.class);
        verify(jobs).complete(eq(job.id()), ids.capture());
        assertThat(ids.getValue()).hasSize(2);
        verify(jobs, never()).fail(any(), anyString());
    }

    @Test
    void oneFailingCandidateDoesNotSpoilTheOthers() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        available(a, List.of(slot(1, 10)));
        available(b, List.of(slot(1, 10)));
        searchReturns(a, b);
        when(assistant.writeCaptions(anyString(), any()))
                .thenThrow(new LlmUnavailableException("model busy"))
                .thenReturn(Map.of(1, "好"));

        handler.handle(job);

        ArgumentCaptor<List<UUID>> ids = ArgumentCaptor.forClass(List.class);
        verify(jobs).complete(eq(job.id()), ids.capture());
        assertThat(ids.getValue()).hasSize(1);
    }

    @Test
    void failsTheJobWhenNoCandidateCouldBeMade() {
        UUID a = UUID.randomUUID();
        available(a, List.of(slot(1, 10)));
        searchReturns(a);
        when(assistant.writeCaptions(anyString(), any())).thenThrow(new LlmUnavailableException("down"));

        handler.handle(job);

        verify(jobs).fail(eq(job.id()), anyString());
        verify(jobs, never()).complete(any(), anyList());
    }

    @Test
    void failsTheJobWhenSearchFindsNothing() {
        searchReturns();

        handler.handle(job);

        verify(jobs).fail(eq(job.id()), anyString());
    }

    @Test
    void failsTheJobWhenSearchItselfBreaks() {
        when(search.handle(anyString(), anyInt())).thenThrow(new IllegalStateException("db down"));

        handler.handle(job);

        verify(jobs).fail(eq(job.id()), anyString());
    }

    @Test
    void cutsCaptionsThatAreLongerThanTheSlotAllows() {
        UUID a = UUID.randomUUID();
        available(a, List.of(slot(1, 3)));
        searchReturns(a);
        when(assistant.writeCaptions(anyString(), any())).thenReturn(Map.of(1, "一二三四五"));

        handler.handle(job);

        ArgumentCaptor<Meme> saved = ArgumentCaptor.forClass(Meme.class);
        verify(memes).save(saved.capture());
        assertThat(saved.getValue().captions()).containsEntry(1, "一二三");
    }

    @Test
    void templateWithoutSlotsNeedsNoCaptionsAndKeepsItsOwnImage() {
        UUID a = UUID.randomUUID();
        available(a, List.of());
        searchReturns(a);

        handler.handle(job);

        verify(assistant, never()).writeCaptions(anyString(), any());
        verify(renderer, never()).render(any(), anyList(), anyMap());
        verify(storage).put(startsWith("memes/"), eq(original), eq("image/png"));
        verify(jobs).complete(eq(job.id()), anyList());
    }

    @Test
    void skipsTemplatesThatAreNoLongerApproved() {
        UUID retired = UUID.randomUUID();
        when(templates.findById(retired)).thenReturn(Optional.of(template(retired, "RETIRED", 1, List.of(slot(1, 10)))));
        searchReturns(retired);

        handler.handle(job);

        verify(jobs).fail(eq(job.id()), anyString());
        verify(memes, never()).save(any());
    }

    @Test
    void removesTheStoredImageWhenSavingTheMemeFails() {
        UUID a = UUID.randomUUID();
        available(a, List.of(slot(1, 10)));
        searchReturns(a);
        when(assistant.writeCaptions(anyString(), any())).thenReturn(Map.of(1, "好"));
        org.mockito.Mockito.doThrow(new IllegalStateException("db down")).when(memes).save(any());

        handler.handle(job);

        verify(storage).delete(startsWith("memes/"));
        verify(jobs).fail(eq(job.id()), anyString());
    }

    private static String startsWith(String prefix) {
        return org.mockito.ArgumentMatchers.startsWith(prefix);
    }
}

package com.memehub.application.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memehub.application.port.out.LlmUnavailableException;
import com.memehub.application.port.out.ObjectStoragePort;
import com.memehub.application.port.out.TaggingQueuePort;
import com.memehub.application.port.out.TemplateReadPort;
import com.memehub.application.port.out.TemplateRepository;
import com.memehub.application.port.out.VisionTaggerPort;
import com.memehub.application.template.query.TemplateView;
import com.memehub.domain.template.MemeProfile;
import com.memehub.domain.template.MemeTemplate;
import com.memehub.domain.template.TemplateId;
import com.memehub.domain.template.TemplateStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaggingHandlersTest {

    private static final TemplateId ID = TemplateId.newId();
    private static final ImageTags A_MEME = new ImageTags(true, "分心男友", "被新事物吸引而冷落舊的",
            List.of("看到新框架就想放棄舊專案"), List.of("誘惑"), List.of("男友", "路人"), "FOREVER");

    private final TemplateRepository templates = mock(TemplateRepository.class);
    private final ApplyTagsHandler apply = new ApplyTagsHandler(templates);

    private MemeTemplate draft(String name) {
        MemeTemplate template = MemeTemplate.draft(ID, name, "library/x.jpg", 600, 400);
        when(templates.getForUpdate(ID)).thenReturn(template);
        return template;
    }

    // ---- ApplyTagsHandler ------------------------------------------------------------------

    @Test
    void publishesAMemeWithWhatTheModelSaw() {
        MemeTemplate template = draft("cat");

        apply.handle(ID.value(), A_MEME);

        assertThat(template.status()).isEqualTo(TemplateStatus.APPROVED);
        assertThat(template.profile().meaning()).contains("新事物");
        assertThat(template.profile().tags()).containsExactly("男友", "路人");
        assertThat(template.profile().imageText()).isEqualTo("FOREVER");
        assertThat(template.profile().usageExamples()).hasSize(1);
        verify(templates).save(template);
    }

    @Test
    void namesAPlaceholderAfterWhatItShowsButKeepsARealNameAndRemembersTheModelsTitleAsAnAlias() {
        MemeTemplate unnamed = draft(IngestMemeHandler.PLACEHOLDER_NAME);
        apply.handle(ID.value(), A_MEME);
        assertThat(unnamed.name()).isEqualTo("分心男友");
        assertThat(unnamed.profile().aliases()).isEmpty();

        MemeTemplate named = draft("Distracted Boyfriend");
        apply.handle(ID.value(), A_MEME);
        assertThat(named.name()).isEqualTo("Distracted Boyfriend");
        assertThat(named.profile().aliases()).containsExactly("分心男友");
    }

    @Test
    void aPictureThatIsNotAMemeIsWithdrawnButKept() {
        MemeTemplate template = draft("holiday photo");

        apply.handle(ID.value(), new ImageTags(false, "風景照", "", List.of(), List.of(), List.of(), ""));

        assertThat(template.status()).isEqualTo(TemplateStatus.RETIRED);
        verify(templates).save(template);
    }

    @Test
    void anIncompleteDescriptionIsAnErrorSoTheTaggingIsTriedAgain() {
        draft("x");

        assertThatThrownBy(() -> apply.handle(ID.value(),
                new ImageTags(true, "t", "意思", List.of(), List.of(), List.of(), "")))
                .isInstanceOf(IllegalStateException.class);
        verify(templates, never()).save(any());
    }

    @Test
    void leavesAnEntryAloneThatSomeoneElseAlreadyDealtWith() {
        MemeTemplate template = draft("x");
        template.reviseProfile(new MemeProfile("手動寫的", List.of("手動範例"), List.of(), List.of()));
        template.approve();

        apply.handle(ID.value(), A_MEME);

        assertThat(template.profile().meaning()).isEqualTo("手動寫的");
        verify(templates, never()).save(any());
    }

    // ---- TagTemplateHandler ----------------------------------------------------------------

    private final TemplateReadPort reads = mock(TemplateReadPort.class);
    private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
    private final VisionTaggerPort tagger = mock(VisionTaggerPort.class);
    private final ApplyTagsHandler applySpy = mock(ApplyTagsHandler.class);
    private final TaggingQueuePort queue = mock(TaggingQueuePort.class);
    private final TagTemplateHandler handler = new TagTemplateHandler(reads, storage, tagger, applySpy, queue, 3);

    private TemplateView view(String status, String name) {
        return new TemplateView(ID.value(), name, status, 1, 600, 400, "library/x.gif", null,
                MemeProfile.empty(), List.of(), Instant.now(), Instant.now());
    }

    @Test
    void looksAtThePictureAppliesTheResultAndMarksItDone() {
        byte[] bytes = {9, 9};
        when(reads.findById(ID.value())).thenReturn(Optional.of(view("DRAFT", "Cat meme")));
        when(storage.get("library/x.gif")).thenReturn(bytes);
        when(tagger.describe(bytes, "image/gif", "Cat meme")).thenReturn(A_MEME);

        handler.handle(ID.value());

        verify(applySpy).handle(ID.value(), A_MEME);
        verify(queue).done(ID.value());
    }

    @Test
    void doesNotPassOnAPlaceholderNameAsAHint() {
        byte[] bytes = {9};
        when(reads.findById(ID.value())).thenReturn(Optional.of(view("DRAFT", IngestMemeHandler.PLACEHOLDER_NAME)));
        when(storage.get(anyString())).thenReturn(bytes);
        when(tagger.describe(any(), anyString(), any())).thenReturn(A_MEME);

        handler.handle(ID.value());

        verify(tagger).describe(bytes, "image/gif", null);
    }

    @Test
    void putsThePictureBackInLineWhenTheModelFails() {
        when(reads.findById(ID.value())).thenReturn(Optional.of(view("DRAFT", "x")));
        when(storage.get(anyString())).thenReturn(new byte[] {1});
        when(tagger.describe(any(), anyString(), any())).thenThrow(new LlmUnavailableException("model busy"));

        handler.handle(ID.value());

        verify(queue).fail(eq(ID.value()), eq("model busy"), eq(3));
        verify(queue, never()).done(any());
        verify(applySpy, never()).handle(any(), any());
    }

    @Test
    void aFailureWhileApplyingAlsoPutsItBackInLine() {
        when(reads.findById(ID.value())).thenReturn(Optional.of(view("DRAFT", "x")));
        when(storage.get(anyString())).thenReturn(new byte[] {1});
        when(tagger.describe(any(), anyString(), any())).thenReturn(A_MEME);
        doThrow(new IllegalStateException("incomplete")).when(applySpy).handle(any(), any());

        handler.handle(ID.value());

        verify(queue).fail(eq(ID.value()), anyString(), anyInt());
    }

    @Test
    void anEntryThatIsGoneOrNoLongerADraftIsJustMarkedDone() {
        when(reads.findById(ID.value())).thenReturn(Optional.empty());
        handler.handle(ID.value());

        when(reads.findById(ID.value())).thenReturn(Optional.of(view("APPROVED", "x")));
        handler.handle(ID.value());

        verify(tagger, never()).describe(any(), anyString(), any());
        verify(queue, org.mockito.Mockito.times(2)).done(ID.value());
    }

    @Test
    void knowsTheContentTypeFromTheFileEnding() {
        assertThat(TagTemplateHandler.contentTypeOf("a.PNG")).isEqualTo("image/png");
        assertThat(TagTemplateHandler.contentTypeOf("a.gif")).isEqualTo("image/gif");
        assertThat(TagTemplateHandler.contentTypeOf("a.jpg")).isEqualTo("image/jpeg");
        assertThat(UUID.randomUUID()).isNotNull();
    }
}

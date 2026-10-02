package com.wtm.application.report;

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

import com.wtm.application.TemplateNotFoundException;
import com.wtm.application.TooManyRequestsException;
import com.wtm.application.port.out.ObjectStoragePort;
import com.wtm.application.port.out.ReportPort;
import com.wtm.application.port.out.ReportPort.OpenReport;
import com.wtm.application.port.out.ReportPort.Resolution;
import com.wtm.application.port.out.ReviewPort;
import com.wtm.application.port.out.ReviewPort.ReviewState;
import com.wtm.application.port.out.TemplateReadPort;
import com.wtm.application.port.out.TemplateRepository;
import com.wtm.application.port.out.VisionTaggerPort;
import com.wtm.application.port.out.LlmUnavailableException;
import com.wtm.application.template.query.TemplateView;
import com.wtm.domain.DomainRuleViolation;
import com.wtm.domain.template.MemeProfile;
import com.wtm.domain.template.MemeTemplate;
import com.wtm.domain.template.TemplateId;
import com.wtm.domain.template.TemplateStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReportHandlersTest {

    private final ReportPort reports = mock(ReportPort.class);
    private final ReviewPort reviews = mock(ReviewPort.class);
    private final TemplateReadPort reads = mock(TemplateReadPort.class);
    private final TemplateRepository templates = mock(TemplateRepository.class);
    private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
    private final VisionTaggerPort tagger = mock(VisionTaggerPort.class);

    private final UUID user = UUID.randomUUID();
    private final UUID memeId = UUID.randomUUID();

    private static final MemeProfile CURRENT = new MemeProfile("舊的意思", List.of("舊情境"), List.of("無奈"),
            List.of("別名"), "舊文字", List.of("舊標籤"));

    private static Suggestion suggestion() {
        return new Suggestion(true, "新的意思", List.of("新情境"), List.of("得意"), List.of("新標籤"), "新文字", "因為圖裡其實是…");
    }

    private OpenReport report(ReportReason reason, String comment) {
        return new OpenReport(UUID.randomUUID(), memeId, "someone", reason, comment, Instant.now());
    }

    // -- submitting -----------------------------------------------------------

    @Test
    void aReportAsksTheModelToLookAgain() {
        when(reports.submit(user, memeId, ReportReason.WRONG_TAGS, "標籤是貓但圖是狗")).thenReturn(true);

        new SubmitReportHandler(reports, reviews).handle(user, memeId, ReportReason.WRONG_TAGS, "  標籤是貓但圖是狗 ");

        verify(reviews).request(memeId);
    }

    @Test
    void reportingAMemeThatIsNotPublishedLooksLikeReportingAMissingOne() {
        when(reports.submit(any(), any(), any(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> new SubmitReportHandler(reports, reviews)
                .handle(user, memeId, ReportReason.OTHER, ""))
                .isInstanceOf(TemplateNotFoundException.class);
        verify(reviews, never()).request(any());
    }

    @Test
    void aReasonIsRequiredAndTheCommentHasALimit() {
        var handler = new SubmitReportHandler(reports, reviews);

        assertThatThrownBy(() -> handler.handle(user, memeId, null, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle(user, memeId, ReportReason.OTHER,
                "字".repeat(SubmitReportHandler.MAX_COMMENT_LENGTH + 1))).isInstanceOf(IllegalArgumentException.class);
        verify(reports, never()).submit(any(), any(), any(), anyString());
    }

    @Test
    void aPersonWithTooManyReportsWaitingMustWait() {
        when(reports.openCountOf(user)).thenReturn(SubmitReportHandler.MAX_OPEN_PER_USER);

        assertThatThrownBy(() -> new SubmitReportHandler(reports, reviews)
                .handle(user, memeId, ReportReason.OTHER, "x")).isInstanceOf(TooManyRequestsException.class);
        verify(reports, never()).submit(any(), any(), any(), anyString());
    }

    // -- the model looks again --------------------------------------------------

    private TemplateView view(String status) {
        return new TemplateView(memeId, "貓", status, 1, 600, 400, "library/a.png", null, CURRENT, List.of(),
                Instant.now(), Instant.now());
    }

    private RunReviewHandler runner() {
        return new RunReviewHandler(reads, storage, tagger, reports, reviews, 3);
    }

    @Test
    void theModelIsToldWhatIsWrongAndItsProposalIsKept() {
        when(reads.findById(memeId)).thenReturn(Optional.of(view("APPROVED")));
        when(reports.openAbout(memeId)).thenReturn(List.of(report(ReportReason.WRONG_TAGS, "是狗不是貓"),
                report(ReportReason.WRONG_MEANING, "")));
        when(storage.get("library/a.png")).thenReturn(new byte[] {1});
        when(tagger.reassess(any(), eq("image/png"), any())).thenReturn(suggestion());

        runner().handle(memeId);

        ArgumentCaptor<ReviewRequest> asked = ArgumentCaptor.forClass(ReviewRequest.class);
        verify(tagger).reassess(any(), eq("image/png"), asked.capture());
        assertThat(asked.getValue().current()).isEqualTo(CURRENT);
        assertThat(asked.getValue().complaints()).containsExactly("標籤不精確:是狗不是貓", "描述不貼切");
        verify(reviews).complete(memeId, suggestion());
    }

    @Test
    void whenThereIsNothingLeftToLookAtTheReviewIsDropped() {
        when(reads.findById(memeId)).thenReturn(Optional.of(view("APPROVED")));
        when(reports.openAbout(memeId)).thenReturn(List.of());

        runner().handle(memeId);

        verify(reviews).delete(memeId);
        verify(tagger, never()).reassess(any(), anyString(), any());
    }

    @Test
    void aMemeThatWasWithdrawnMeanwhileIsNotLookedAt() {
        when(reads.findById(memeId)).thenReturn(Optional.of(view("RETIRED")));
        when(reports.openAbout(memeId)).thenReturn(List.of(report(ReportReason.INAPPROPRIATE, "")));

        runner().handle(memeId);

        verify(reviews).delete(memeId);
        verify(tagger, never()).reassess(any(), anyString(), any());
    }

    @Test
    void aFailureGoesBackInLineWithTheReason() {
        when(reads.findById(memeId)).thenReturn(Optional.of(view("APPROVED")));
        when(reports.openAbout(memeId)).thenReturn(List.of(report(ReportReason.WRONG_TAGS, "x")));
        when(storage.get(anyString())).thenReturn(new byte[] {1});
        when(tagger.reassess(any(), anyString(), any())).thenThrow(new LlmUnavailableException("model is down"));

        runner().handle(memeId);

        verify(reviews).fail(memeId, "model is down", 3);
        verify(reviews, never()).complete(any(), any());
    }

    // -- the administrator decides ----------------------------------------------

    private MemeTemplate approved() {
        return MemeTemplate.restore(new TemplateId(memeId), "貓", "library/a.png", 600, 400,
                TemplateStatus.APPROVED, 1, CURRENT, List.of());
    }

    @Test
    void adoptingTheProposalReplacesTheDescriptionButKeepsTheOtherNames() {
        MemeTemplate template = approved();
        when(reviews.find(memeId)).thenReturn(Optional.of(new ReviewState("DONE", suggestion(), null)));
        when(templates.getForUpdate(new TemplateId(memeId))).thenReturn(template);

        new ResolveReportsHandler(reports, reviews, templates).apply(memeId);

        assertThat(template.profile().meaning()).isEqualTo("新的意思");
        assertThat(template.profile().tags()).containsExactly("新標籤");
        assertThat(template.profile().imageText()).isEqualTo("新文字");
        assertThat(template.profile().aliases()).containsExactly("別名");
        assertThat(template.status()).isEqualTo(TemplateStatus.APPROVED);
        verify(templates).save(template);
        verify(reports).resolve(memeId, Resolution.APPLIED);
        verify(reviews).delete(memeId);
    }

    @Test
    void thereIsNothingToAdoptBeforeTheModelHasAnswered() {
        when(reviews.find(memeId)).thenReturn(Optional.of(new ReviewState("RUNNING", null, null)));

        assertThatThrownBy(() -> new ResolveReportsHandler(reports, reviews, templates).apply(memeId))
                .isInstanceOf(DomainRuleViolation.class);
        verify(reports, never()).resolve(any(), any());
    }

    @Test
    void aProposalWithoutAMeaningIsNotAdopted() {
        var empty = new Suggestion(false, "", List.of(), List.of(), List.of(), "", "不是梗圖");
        when(reviews.find(memeId)).thenReturn(Optional.of(new ReviewState("DONE", empty, null)));
        when(templates.getForUpdate(any())).thenReturn(approved());

        assertThatThrownBy(() -> new ResolveReportsHandler(reports, reviews, templates).apply(memeId))
                .isInstanceOf(DomainRuleViolation.class);
        verify(templates, never()).save(any());
        verify(reports, never()).resolve(any(), any());
    }

    @Test
    void dismissingClosesTheReportsAndLeavesTheMemeAlone() {
        new ResolveReportsHandler(reports, reviews, templates).dismiss(memeId);

        verify(reports).resolve(memeId, Resolution.DISMISSED);
        verify(reviews).delete(memeId);
        verify(templates, never()).save(any());
    }

    @Test
    void lookingAgainNeedsAnOpenReport() {
        var handler = new ResolveReportsHandler(reports, reviews, templates);
        when(reports.openAbout(memeId)).thenReturn(List.of());

        assertThatThrownBy(() -> handler.reanalyze(memeId)).isInstanceOf(DomainRuleViolation.class);

        when(reports.openAbout(memeId)).thenReturn(List.of(report(ReportReason.WRONG_TAGS, "x")));
        handler.reanalyze(memeId);
        verify(reviews).request(memeId);
    }

    @Test
    void theListPutsComplaintsAndProposalSideBySide() {
        when(reports.open()).thenReturn(List.of(report(ReportReason.WRONG_TAGS, "x"), report(ReportReason.WRONG_MEANING, "y")));
        when(reads.findById(memeId)).thenReturn(Optional.of(view("APPROVED")));
        when(reviews.find(memeId)).thenReturn(Optional.of(new ReviewState("DONE", suggestion(), null)));
        when(storage.presignedGetUrl(anyString(), any())).thenReturn("https://storage/a");

        var cases = new ListReportCasesHandler(reports, reviews, reads, storage).handle();

        assertThat(cases).singleElement().satisfies(c -> {
            assertThat(c.reports()).hasSize(2);
            assertThat(c.current().meaning()).isEqualTo("舊的意思");
            assertThat(c.review().suggestion().meaning()).isEqualTo("新的意思");
            assertThat(c.imageUrl()).isEqualTo("https://storage/a");
        });
        verify(reviews, never()).claim(anyInt());
    }
}

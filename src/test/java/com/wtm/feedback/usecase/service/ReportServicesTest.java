package com.wtm.feedback.usecase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtm.curation.usecase.port.in.command.RestoreProfileUseCase;
import com.wtm.curation.usecase.port.in.command.ReviseProfileFromProposalUseCase;
import com.wtm.curation.usecase.port.in.query.GetTemplateUseCase;
import com.wtm.curation.usecase.port.in.query.TemplateView;
import com.wtm.feedback.entity.ReportPolicy;
import com.wtm.feedback.entity.ReportPolicy.Standing;
import com.wtm.feedback.entity.ReportReason;
import com.wtm.feedback.usecase.port.in.command.SubmitReportUseCase;
import com.wtm.feedback.usecase.port.out.report.ReportPort;
import com.wtm.feedback.usecase.port.out.report.ReportPort.AutomaticAction;
import com.wtm.feedback.usecase.port.out.report.ReportPort.OpenReport;
import com.wtm.feedback.usecase.port.out.report.ReportPort.Resolution;
import com.wtm.feedback.usecase.port.out.review.ReviewPort;
import com.wtm.feedback.usecase.port.out.review.ReviewPort.ReviewState;
import com.wtm.shared.DomainRuleViolation;
import com.wtm.shared.MemeProfile;
import com.wtm.shared.TemplateId;
import com.wtm.shared.TemplateNotFoundException;
import com.wtm.shared.TooManyRequestsException;
import com.wtm.shared.port.LlmUnavailableException;
import com.wtm.shared.port.ObjectStoragePort;
import com.wtm.shared.port.ReviewRequest;
import com.wtm.shared.port.Suggestion;
import com.wtm.shared.port.VisionTaggerPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReportServicesTest {

    private final ReportPort reports = mock(ReportPort.class);
    private final ReviewPort reviews = mock(ReviewPort.class);
    private final GetTemplateUseCase reads = mock(GetTemplateUseCase.class);
    private final ReviseProfileFromProposalUseCase revise = mock(ReviseProfileFromProposalUseCase.class);
    private final RestoreProfileUseCase restore = mock(RestoreProfileUseCase.class);
    private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
    private final VisionTaggerPort tagger = mock(VisionTaggerPort.class);

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ReportJudge judge = new ReportJudge(reports, ReportPolicy.standard());

    private final UUID user = UUID.randomUUID();
    private final UUID memeId = UUID.randomUUID();

    private static final MemeProfile CURRENT = new MemeProfile("舊的意思", List.of("舊情境"), List.of("無奈"),
            List.of("別名"), "舊文字", List.of("舊標籤"));

    private static Suggestion suggestion() {
        return new Suggestion(true, "新的意思", List.of("新情境"), List.of("得意"), List.of("新標籤"), "新文字", "因為圖裡其實是…");
    }

    private static final Standing NEWCOMER = new Standing(false, 0, 0);

    private OpenReport report(UUID by, ReportReason reason, String comment) {
        return new OpenReport(UUID.randomUUID(), memeId, by, "someone", reason, comment, Instant.now());
    }

    private OpenReport report(ReportReason reason, String comment) {
        return report(UUID.randomUUID(), reason, comment);
    }

    {
        // Unless a test says otherwise, everybody is a newcomer.
        when(reports.standingOf(any())).thenReturn(NEWCOMER);
    }

    private SubmitReportService submitter() {
        return new SubmitReportService(reports, reviews, judge, resolver, clock);
    }

    // -- submitting -----------------------------------------------------------

    @Test
    void aReportIsRecordedAndWhenEnoughPeopleAgreeTheModelIsAsked() {
        when(reports.submit(user, memeId, ReportReason.WRONG_TAGS, "標籤是貓但圖是狗")).thenReturn(true);
        when(reports.openAbout(memeId)).thenReturn(List.of(report(ReportReason.WRONG_TAGS, "a"),
                report(ReportReason.WRONG_TAGS, "b")));
        when(reviews.lastRunAt(memeId)).thenReturn(Optional.empty());

        submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.WRONG_TAGS, "  標籤是貓但圖是狗 "));

        verify(reviews).request(memeId, false);
    }

    @Test
    void aLoneReportFromANewcomerIsRecordedButCostsNoModelTime() {
        when(reports.submit(any(), any(), any(), anyString())).thenReturn(true);
        when(reports.openAbout(memeId)).thenReturn(List.of(report(ReportReason.WRONG_TAGS, "a")));

        submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.WRONG_TAGS, "a"));

        verify(reviews, never()).request(any(), anyBoolean());
    }

    @Test
    void aHabitualFalseReporterCountsForNothing() {
        UUID liar = UUID.randomUUID();
        when(reports.standingOf(liar)).thenReturn(new Standing(false, 0, ReportPolicy.standard().lowTrustDismissals()));
        when(reports.submit(any(), any(), any(), anyString())).thenReturn(true);
        when(reports.openAbout(memeId)).thenReturn(List.of(report(liar, ReportReason.OTHER, "x"),
                report(ReportReason.OTHER, "y")));

        submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.OTHER, "x"));

        verify(reviews, never()).request(any(), anyBoolean());
    }

    @Test
    void anAdministratorsReportAloneGetsTheModelsLook() {
        UUID admin = UUID.randomUUID();
        when(reports.standingOf(admin)).thenReturn(new Standing(true, 0, 0));
        when(reports.submit(any(), any(), any(), anyString())).thenReturn(true);
        when(reports.openAbout(memeId)).thenReturn(List.of(report(admin, ReportReason.WRONG_MEANING, "x")));
        when(reviews.lastRunAt(memeId)).thenReturn(Optional.empty());

        submitter().handle(new SubmitReportUseCase.Input(admin, memeId, ReportReason.WRONG_MEANING, "x"));

        verify(reviews).request(memeId, false);
    }

    @Test
    void aMemeThatWasJustLookedAtIsLeftAloneWhateverIsReportedNext() {
        when(reports.submit(any(), any(), any(), anyString())).thenReturn(true);
        when(reports.openAbout(memeId)).thenReturn(List.of(report(ReportReason.OTHER, "a"), report(ReportReason.OTHER, "b")));
        when(reviews.lastRunAt(memeId)).thenReturn(Optional.of(NOW.minus(Duration.ofHours(1))));

        submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.OTHER, "again"));

        verify(reviews, never()).request(any(), anyBoolean());
    }

    @Test
    void afterTheCooldownAMemeCanBeLookedAtAgain() {
        when(reports.submit(any(), any(), any(), anyString())).thenReturn(true);
        when(reports.openAbout(memeId)).thenReturn(List.of(report(ReportReason.OTHER, "a"), report(ReportReason.OTHER, "b")));
        when(reviews.lastRunAt(memeId)).thenReturn(Optional.of(NOW.minus(Duration.ofHours(25))));

        submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.OTHER, "again"));

        verify(reviews).request(memeId, false);
    }

    @Test
    void whenAnotherPersonJoinsAndTheyNowAgreeEnoughTheEarlierProposalIsAdoptedWithoutAskingTheModelAgain() {
        when(reports.submit(any(), any(), any(), anyString())).thenReturn(true);
        when(reports.openAbout(memeId)).thenReturn(reporters(3, ReportReason.WRONG_TAGS));
        when(reviews.find(memeId)).thenReturn(Optional.of(new ReviewState("DONE", suggestion(), null, false)));

        submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.WRONG_TAGS, "me too"));

        verify(resolver).applyAutomatically(memeId);
        verify(reviews, never()).request(any(), anyBoolean());
    }

    @Test
    void anEarlierProposalIsNotAdoptedBeforeEnoughPeopleAgree() {
        when(reports.submit(any(), any(), any(), anyString())).thenReturn(true);
        when(reports.openAbout(memeId)).thenReturn(reporters(2, ReportReason.WRONG_TAGS));
        when(reviews.find(memeId)).thenReturn(Optional.of(new ReviewState("DONE", suggestion(), null, false)));
        when(reviews.lastRunAt(memeId)).thenReturn(Optional.of(NOW.minus(Duration.ofHours(1))));

        submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.WRONG_TAGS, "me too"));

        verify(resolver, never()).applyAutomatically(any());
    }

    @Test
    void aProposalAnAdministratorAskedForIsNotAdoptedByTheRulesWhenMorePeopleJoin() {
        when(reports.submit(any(), any(), any(), anyString())).thenReturn(true);
        when(reports.openAbout(memeId)).thenReturn(reporters(3, ReportReason.WRONG_TAGS));
        when(reviews.find(memeId)).thenReturn(Optional.of(new ReviewState("DONE", suggestion(), null, true)));
        when(reviews.lastRunAt(memeId)).thenReturn(Optional.of(NOW.minus(Duration.ofHours(1))));

        submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.WRONG_TAGS, "me too"));

        verify(resolver, never()).applyAutomatically(any());
    }

    @Test
    void reportingAMemeThatIsNotPublishedLooksLikeReportingAMissingOne() {
        when(reports.submit(any(), any(), any(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.OTHER, "")))
                .isInstanceOf(TemplateNotFoundException.class);
        verify(reviews, never()).request(any(), anyBoolean());
    }

    @Test
    void aReasonIsRequiredAndTheCommentHasALimit() {
        var handler = submitter();

        assertThatThrownBy(() -> handler.handle(new SubmitReportUseCase.Input(user, memeId, null, "x"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.OTHER,
                "字".repeat(SubmitReportService.MAX_COMMENT_LENGTH + 1)))).isInstanceOf(IllegalArgumentException.class);
        verify(reports, never()).submit(any(), any(), any(), anyString());
    }

    @Test
    void aPersonWithTooManyReportsWaitingMustWait() {
        when(reports.openCountOf(user)).thenReturn(SubmitReportService.MAX_OPEN_PER_USER);

        assertThatThrownBy(() -> submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.OTHER, "x")))
                .isInstanceOf(TooManyRequestsException.class);
        verify(reports, never()).submit(any(), any(), any(), anyString());
    }

    @Test
    void aPersonCanOnlyReportSoManyDifferentMemesADay() {
        when(reports.reportedSince(user, NOW.minus(Duration.ofHours(24)), memeId))
                .thenReturn(ReportPolicy.standard().dailyReportsPerUser());

        assertThatThrownBy(() -> submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.OTHER, "x")))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("today");
        verify(reports, never()).submit(any(), any(), any(), anyString());
    }

    // -- the model looks again --------------------------------------------------

    private TemplateView view(String status) {
        return new TemplateView(memeId, "貓", status, 1, 600, 400, "library/a.png", null, CURRENT, List.of(),
                Instant.now(), Instant.now());
    }

    private final ResolveReportsService resolver = mock(ResolveReportsService.class);

    private RunReviewService runner() {
        return new RunReviewService(reads, storage, tagger, reports, reviews, judge, resolver, 3);
    }

    private void theModelAnswers(Suggestion answer, List<OpenReport> open) {
        when(reads.handle(memeId)).thenReturn(view("APPROVED"));
        when(reports.openAbout(memeId)).thenReturn(open);
        when(storage.get("library/a.png")).thenReturn(new byte[] {1});
        when(tagger.reassess(any(), eq("image/png"), any())).thenReturn(answer);
    }

    @Test
    void theModelIsToldWhatIsWrongAndItsProposalIsKept() {
        theModelAnswers(suggestion(), List.of(report(ReportReason.WRONG_TAGS, "是狗不是貓"),
                report(ReportReason.WRONG_MEANING, "")));

        runner().handle(memeId);

        ArgumentCaptor<ReviewRequest> asked = ArgumentCaptor.forClass(ReviewRequest.class);
        verify(tagger).reassess(any(), eq("image/png"), asked.capture());
        assertThat(asked.getValue().current()).isEqualTo(CURRENT);
        assertThat(asked.getValue().complaints()).containsExactly("標籤不精確:是狗不是貓", "描述不貼切");
        verify(reviews).complete(memeId, suggestion());
    }

    @Test
    void theModelIsToldWhenPeopleSayItIsNotAMeme() {
        theModelAnswers(suggestion(), List.of(report(ReportReason.NOT_A_MEME, "只是一張生活照")));

        runner().handle(memeId);

        ArgumentCaptor<ReviewRequest> asked = ArgumentCaptor.forClass(ReviewRequest.class);
        verify(tagger).reassess(any(), eq("image/png"), asked.capture());
        assertThat(asked.getValue().complaints()).containsExactly("這不是梗圖:只是一張生活照");
    }

    @Test
    void whenThereIsNothingLeftToLookAtTheReviewIsDropped() {
        when(reads.handle(memeId)).thenReturn(view("APPROVED"));
        when(reports.openAbout(memeId)).thenReturn(List.of());

        runner().handle(memeId);

        verify(reviews).delete(memeId);
        verify(tagger, never()).reassess(any(), anyString(), any());
    }

    @Test
    void aMemeThatWasWithdrawnMeanwhileIsNotLookedAt() {
        when(reads.handle(memeId)).thenReturn(view("RETIRED"));
        when(reports.openAbout(memeId)).thenReturn(List.of(report(ReportReason.INAPPROPRIATE, "")));

        runner().handle(memeId);

        verify(reviews).delete(memeId);
        verify(tagger, never()).reassess(any(), anyString(), any());
    }

    @Test
    void aFailureGoesBackInLineWithTheReason() {
        when(reads.handle(memeId)).thenReturn(view("APPROVED"));
        when(reports.openAbout(memeId)).thenReturn(List.of(report(ReportReason.WRONG_TAGS, "x")));
        when(storage.get(anyString())).thenReturn(new byte[] {1});
        when(tagger.reassess(any(), anyString(), any())).thenThrow(new LlmUnavailableException("model is down"));

        runner().handle(memeId);

        verify(reviews).fail(memeId, "model is down", 3);
        verify(reviews, never()).complete(any(), any());
    }

    // -- the rules act on their own ------------------------------------------------

    private List<OpenReport> reporters(int count, ReportReason reason) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> report(reason, "c" + i)).toList();
    }

    @Test
    void whenTheModelFindsNothingWrongAndFewInsistTheReportsAreClosedByThemselves() {
        theModelAnswers(new Suggestion(true, "舊的意思", List.of("舊情境"), List.of(), List.of("舊標籤"), "", "描述正確", true),
                reporters(2, ReportReason.WRONG_TAGS));

        runner().handle(memeId);

        verify(resolver).dismissAutomatically(memeId, "描述正確");
        verify(resolver, never()).applyAutomatically(any());
    }

    @Test
    void whenEnoughPeopleAgreeAndTheModelProposesAChangeItIsAdoptedByItself() {
        theModelAnswers(suggestion(), reporters(3, ReportReason.WRONG_TAGS));

        runner().handle(memeId);

        verify(resolver).applyAutomatically(memeId);
        verify(resolver, never()).dismissAutomatically(any(), any());
    }

    @Test
    void whenTheyDoNotAgreeEnoughAnAdministratorDecides() {
        theModelAnswers(suggestion(), reporters(2, ReportReason.WRONG_TAGS));

        runner().handle(memeId);

        verify(resolver, never()).applyAutomatically(any());
        verify(resolver, never()).dismissAutomatically(any(), any());
    }

    @Test
    void whatAnAdministratorAskedForIsNeverActedOnByTheRules() {
        theModelAnswers(suggestion(), reporters(3, ReportReason.WRONG_TAGS));
        when(reviews.find(memeId)).thenReturn(Optional.of(new ReviewState("RUNNING", null, null, true)));

        runner().handle(memeId);

        verify(reviews).complete(memeId, suggestion());
        verify(resolver, never()).applyAutomatically(any());
        verify(resolver, never()).dismissAutomatically(any(), any());
    }

    @Test
    void aFailedShortcutLeavesTheProposalForTheAdministratorInsteadOfFailingTheReview() {
        theModelAnswers(suggestion(), reporters(3, ReportReason.WRONG_TAGS));
        doThrow(new DomainRuleViolation("no")).when(resolver).applyAutomatically(memeId);

        runner().handle(memeId);

        verify(reviews).complete(memeId, suggestion());
        verify(reviews, never()).fail(any(), anyString(), anyInt());
    }

    // -- the administrator decides ----------------------------------------------

    private ResolveReportsService resolving() {
        return new ResolveReportsService(reports, reviews, revise, restore, clock);
    }

    @Test
    void adoptingTheProposalHandsItToCurationAndClosesTheReports() {
        when(reviews.find(memeId)).thenReturn(Optional.of(new ReviewState("DONE", suggestion(), null, false)));

        resolving().apply(memeId);

        verify(revise).handle(new ReviseProfileFromProposalUseCase.Input(memeId, "新的意思", List.of("新情境"),
                List.of("得意"), "新文字", List.of("新標籤"), false));
        verify(reports).resolve(memeId, Resolution.APPLIED, false, "因為圖裡其實是…");
        verify(reviews).delete(memeId);
    }

    @Test
    void anAutomaticAdoptionIsMarkedAsSuchSoItCanBeTakenBack() {
        when(reviews.find(memeId)).thenReturn(Optional.of(new ReviewState("DONE", suggestion(), null, false)));

        resolving().applyAutomatically(memeId);

        verify(revise).handle(org.mockito.ArgumentMatchers.argThat(input -> input.automatic()));
        verify(reports).resolve(eq(memeId), eq(Resolution.APPLIED), eq(true), anyString());
    }

    @Test
    void thereIsNothingToAdoptBeforeTheModelHasAnswered() {
        when(reviews.find(memeId)).thenReturn(Optional.of(new ReviewState("RUNNING", null, null, false)));

        assertThatThrownBy(() -> resolving().apply(memeId)).isInstanceOf(DomainRuleViolation.class);
        verify(reports, never()).resolve(any(), any(), anyBoolean(), any());
    }

    @Test
    void aProposalCurationRefusesIsNotAdopted() {
        var empty = new Suggestion(false, "", List.of(), List.of(), List.of(), "", "不是梗圖");
        when(reviews.find(memeId)).thenReturn(Optional.of(new ReviewState("DONE", empty, null, false)));
        doThrow(new DomainRuleViolation("no")).when(revise).handle(any());

        assertThatThrownBy(() -> resolving().apply(memeId)).isInstanceOf(DomainRuleViolation.class);
        verify(reports, never()).resolve(any(), any(), anyBoolean(), any());
    }

    @Test
    void dismissingClosesTheReportsAndLeavesTheMemeAlone() {
        resolving().dismiss(memeId);

        verify(reports).resolve(memeId, Resolution.DISMISSED, false, null);
        verify(reviews).delete(memeId);
        verify(revise, never()).handle(any());
    }

    @Test
    void lookingAgainNeedsAnOpenReportAndIgnoresTheBudget() {
        var handler = resolving();
        when(reports.openAbout(memeId)).thenReturn(List.of());

        assertThatThrownBy(() -> handler.reanalyze(memeId)).isInstanceOf(DomainRuleViolation.class);

        when(reports.openAbout(memeId)).thenReturn(List.of(report(ReportReason.WRONG_TAGS, "x")));
        handler.reanalyze(memeId);
        verify(reviews).request(memeId, true);
    }

    // -- taking an automatic decision back ----------------------------------------

    @Test
    void anAutomaticChangeIsPutBackByCuration() {
        when(restore.handle(memeId)).thenReturn(true);

        resolving().undoAutomatic(memeId);

        verify(restore).handle(memeId);
    }

    @Test
    void aChangeCurationWillNotPutBackIsReportedAndLeavesTheReportsAlone() {
        when(restore.handle(memeId)).thenThrow(new DomainRuleViolation("changed since"));

        assertThatThrownBy(() -> resolving().undoAutomatic(memeId)).isInstanceOf(DomainRuleViolation.class);
        verify(reports, never()).reopenAutomaticallyDismissed(any(), any());
    }

    @Test
    void automaticallyClosedReportsCanBeOpenedAgainAndTheModelLooksOnceMore() {
        when(restore.handle(memeId)).thenReturn(false);
        when(reports.reopenAutomaticallyDismissed(eq(memeId), any())).thenReturn(2);

        resolving().undoAutomatic(memeId);

        verify(reports).reopenAutomaticallyDismissed(memeId, NOW.minus(ResolveReportsService.UNDO_WINDOW));
        verify(reviews).request(memeId, true);
    }

    @Test
    void whenTheRulesDidNothingThereIsNothingToTakeBack() {
        when(restore.handle(memeId)).thenReturn(false);
        when(reports.reopenAutomaticallyDismissed(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> resolving().undoAutomatic(memeId)).isInstanceOf(DomainRuleViolation.class);
    }

    // -- the administrator's list --------------------------------------------------

    private ListReportCasesService lister() {
        return new ListReportCasesService(reports, reviews, reads, storage, restore, judge, clock);
    }

    @Test
    void theListPutsComplaintsAndProposalSideBySideWithTheirWeight() {
        when(reports.open()).thenReturn(List.of(report(ReportReason.WRONG_TAGS, "x"), report(ReportReason.WRONG_MEANING, "y")));
        when(reads.handle(memeId)).thenReturn(view("APPROVED"));
        when(reviews.find(memeId)).thenReturn(Optional.of(new ReviewState("DONE", suggestion(), null, false)));
        when(storage.presignedGetUrl(anyString(), any())).thenReturn("https://storage/a");

        var cases = lister().handle();

        assertThat(cases).singleElement().satisfies(c -> {
            assertThat(c.reports()).hasSize(2);
            assertThat(c.current().meaning()).isEqualTo("舊的意思");
            assertThat(c.review().suggestion().meaning()).isEqualTo("新的意思");
            assertThat(c.imageUrl()).isEqualTo("https://storage/a");
            assertThat(c.weight()).isEqualTo(2);
            assertThat(c.neededWeight()).isEqualTo(ReportPolicy.standard().analyzeAtWeight());
        });
    }

    @Test
    void whatTheRulesDecidedIsListedWithWhetherItCanBeTakenBack() {
        when(reports.automaticSince(NOW.minus(ResolveReportsService.UNDO_WINDOW))).thenReturn(List.of(
                new AutomaticAction(memeId, Resolution.APPLIED, 3, "改了標籤", NOW),
                new AutomaticAction(UUID.randomUUID(), Resolution.DISMISSED, 1, "沒問題", NOW)));
        when(reads.handle(memeId)).thenReturn(view("APPROVED"));
        when(restore.canRestore(memeId)).thenReturn(false);   // already taken back
        when(reads.handle(org.mockito.ArgumentMatchers.argThat(id -> !memeId.equals(id))))
                .thenThrow(new TemplateNotFoundException(new TemplateId(UUID.randomUUID())));
        when(storage.presignedGetUrl(anyString(), any())).thenReturn("https://storage/a");

        var entries = lister().automatic();

        assertThat(entries).singleElement().satisfies(e -> {
            assertThat(e.action()).isEqualTo("APPLIED");
            assertThat(e.canUndo()).isFalse();
        });
    }
}

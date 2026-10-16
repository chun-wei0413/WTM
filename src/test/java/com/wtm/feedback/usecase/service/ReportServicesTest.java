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
import com.wtm.feedback.entity.Report;
import com.wtm.feedback.entity.ReportCase;
import com.wtm.feedback.entity.ReportPolicy;
import com.wtm.feedback.entity.ReportPolicy.Standing;
import com.wtm.feedback.entity.ReportReason;
import com.wtm.feedback.entity.Resolution;
import com.wtm.feedback.usecase.port.in.command.SubmitReportUseCase;
import com.wtm.feedback.usecase.port.in.query.OpenReport;
import com.wtm.feedback.usecase.port.out.report.ReportCaseRepository;
import com.wtm.feedback.usecase.port.out.report.ReportViewPort;
import com.wtm.feedback.usecase.port.out.report.ReportViewPort.AutomaticAction;
import com.wtm.feedback.usecase.port.out.report.ReporterPort;
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

    private final FakeCases cases = new FakeCases();
    private final ReporterPort reporters = mock(ReporterPort.class);
    private final ReportViewPort view = mock(ReportViewPort.class);
    private final ReviewPort reviews = mock(ReviewPort.class);
    private final GetTemplateUseCase reads = mock(GetTemplateUseCase.class);
    private final ReviseProfileFromProposalUseCase revise = mock(ReviseProfileFromProposalUseCase.class);
    private final RestoreProfileUseCase restore = mock(RestoreProfileUseCase.class);
    private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
    private final VisionTaggerPort tagger = mock(VisionTaggerPort.class);

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ReportJudge judge = new ReportJudge(reporters, ReportPolicy.standard());

    private final UUID user = UUID.randomUUID();
    private final UUID memeId = UUID.randomUUID();

    private static final MemeProfile CURRENT = new MemeProfile("舊的意思", List.of("舊情境"), List.of("無奈"),
            List.of("別名"), "舊文字", List.of("舊標籤"));

    private static Suggestion suggestion() {
        return new Suggestion(true, "新的意思", List.of("新情境"), List.of("得意"), List.of("新標籤"), "新文字", "因為圖裡其實是…");
    }

    private static final Standing NEWCOMER = new Standing(false, 0, 0);

    private Report report(UUID by, ReportReason reason, String comment) {
        return new Report(UUID.randomUUID(), by, reason, comment, Instant.now(), null);
    }

    /** Puts these reports about the meme in the store, as if people had made them earlier. */
    private void reported(List<Report> reports) {
        cases.put(ReportCase.restore(memeId, reports));
    }

    private Report report(ReportReason reason, String comment) {
        return report(UUID.randomUUID(), reason, comment);
    }

    {
        // Unless a test says otherwise, everybody is a newcomer.
        when(reporters.standingOf(any())).thenReturn(NEWCOMER);
        when(reads.handle(memeId)).thenReturn(view("APPROVED"));
    }

    private SubmitReportService submitter() {
        return new SubmitReportService(cases, reporters, reads, reviews, judge, resolver, clock);
    }

    // -- submitting -----------------------------------------------------------

    @Test
    void aReportIsRecordedAndWhenEnoughPeopleAgreeTheModelIsAsked() {
        reported(List.of(report(ReportReason.WRONG_TAGS, "a")));
        when(reviews.lastRunAt(memeId)).thenReturn(Optional.empty());

        submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.WRONG_TAGS, "  標籤是貓但圖是狗 "));

        assertThat(cases.get(memeId).openReports()).extracting(Report::comment).contains("標籤是貓但圖是狗");
        verify(reviews).request(memeId, false);
    }

    @Test
    void aLoneReportFromANewcomerIsRecordedButCostsNoModelTime() {
        submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.WRONG_TAGS, "a"));

        verify(reviews, never()).request(any(), anyBoolean());
    }

    @Test
    void aHabitualFalseReporterCountsForNothing() {
        UUID liar = UUID.randomUUID();
        when(reporters.standingOf(liar)).thenReturn(new Standing(false, 0, ReportPolicy.standard().lowTrustDismissals()));
        reported(List.of(report(liar, ReportReason.OTHER, "x")));

        submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.OTHER, "x"));

        verify(reviews, never()).request(any(), anyBoolean());
    }

    @Test
    void anAdministratorsReportAloneGetsTheModelsLook() {
        UUID admin = UUID.randomUUID();
        when(reporters.standingOf(admin)).thenReturn(new Standing(true, 0, 0));
        when(reviews.lastRunAt(memeId)).thenReturn(Optional.empty());

        submitter().handle(new SubmitReportUseCase.Input(admin, memeId, ReportReason.WRONG_MEANING, "x"));

        verify(reviews).request(memeId, false);
    }

    @Test
    void aMemeThatWasJustLookedAtIsLeftAloneWhateverIsReportedNext() {
        reported(List.of(report(ReportReason.OTHER, "a")));
        when(reviews.lastRunAt(memeId)).thenReturn(Optional.of(NOW.minus(Duration.ofHours(1))));

        submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.OTHER, "again"));

        verify(reviews, never()).request(any(), anyBoolean());
    }

    @Test
    void afterTheCooldownAMemeCanBeLookedAtAgain() {
        reported(List.of(report(ReportReason.OTHER, "a")));
        when(reviews.lastRunAt(memeId)).thenReturn(Optional.of(NOW.minus(Duration.ofHours(25))));

        submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.OTHER, "again"));

        verify(reviews).request(memeId, false);
    }

    @Test
    void whenAnotherPersonJoinsAndTheyNowAgreeEnoughTheEarlierProposalIsAdoptedWithoutAskingTheModelAgain() {
        reported(reporters(2, ReportReason.WRONG_TAGS));
        when(reviews.find(memeId)).thenReturn(Optional.of(new ReviewState("DONE", suggestion(), null, false)));

        submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.WRONG_TAGS, "me too"));

        verify(resolver).applyAutomatically(memeId);
        verify(reviews, never()).request(any(), anyBoolean());
    }

    @Test
    void anEarlierProposalIsNotAdoptedBeforeEnoughPeopleAgree() {
        reported(reporters(1, ReportReason.WRONG_TAGS));
        when(reviews.find(memeId)).thenReturn(Optional.of(new ReviewState("DONE", suggestion(), null, false)));
        when(reviews.lastRunAt(memeId)).thenReturn(Optional.of(NOW.minus(Duration.ofHours(1))));

        submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.WRONG_TAGS, "me too"));

        verify(resolver, never()).applyAutomatically(any());
    }

    @Test
    void aProposalAnAdministratorAskedForIsNotAdoptedByTheRulesWhenMorePeopleJoin() {
        reported(reporters(2, ReportReason.WRONG_TAGS));
        when(reviews.find(memeId)).thenReturn(Optional.of(new ReviewState("DONE", suggestion(), null, true)));
        when(reviews.lastRunAt(memeId)).thenReturn(Optional.of(NOW.minus(Duration.ofHours(1))));

        submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.WRONG_TAGS, "me too"));

        verify(resolver, never()).applyAutomatically(any());
    }

    @Test
    void reportingAMemeThatIsNotPublishedLooksLikeReportingAMissingOne() {
        when(reads.handle(memeId)).thenReturn(view("RETIRED"));

        assertThatThrownBy(() -> submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.OTHER, "")))
                .isInstanceOf(TemplateNotFoundException.class);
        verify(reviews, never()).request(any(), anyBoolean());
    }

    @Test
    void aReasonIsRequiredAndTheCommentHasALimit() {
        var handler = submitter();

        assertThatThrownBy(() -> handler.handle(new SubmitReportUseCase.Input(user, memeId, null, "x"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.OTHER,
                "字".repeat(ReportCase.MAX_COMMENT_LENGTH + 1)))).isInstanceOf(IllegalArgumentException.class);
        assertThat(cases.get(memeId).reports()).isEmpty();
    }

    @Test
    void aPersonWithTooManyReportsWaitingMustWait() {
        when(reporters.openCountOf(user)).thenReturn(SubmitReportService.MAX_OPEN_PER_USER);

        assertThatThrownBy(() -> submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.OTHER, "x")))
                .isInstanceOf(TooManyRequestsException.class);
        assertThat(cases.get(memeId).reports()).isEmpty();
    }

    @Test
    void aPersonCanOnlyReportSoManyDifferentMemesADay() {
        when(reporters.reportedSince(user, NOW.minus(Duration.ofHours(24)), memeId))
                .thenReturn(ReportPolicy.standard().dailyReportsPerUser());

        assertThatThrownBy(() -> submitter().handle(new SubmitReportUseCase.Input(user, memeId, ReportReason.OTHER, "x")))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("today");
        assertThat(cases.get(memeId).reports()).isEmpty();
    }

    // -- the model looks again --------------------------------------------------

    private TemplateView view(String status) {
        return new TemplateView(memeId, "貓", status, 1, 600, 400, "library/a.png", null, CURRENT, List.of(),
                Instant.now(), Instant.now());
    }

    private final ResolveReportsService resolver = mock(ResolveReportsService.class);

    private RunReviewService runner() {
        return new RunReviewService(reads, storage, tagger, cases, reviews, judge, resolver, 3);
    }

    private void theModelAnswers(Suggestion answer, List<Report> open) {
        when(reads.handle(memeId)).thenReturn(view("APPROVED"));
        reported(open);
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

        runner().handle(memeId);

        verify(reviews).delete(memeId);
        verify(tagger, never()).reassess(any(), anyString(), any());
    }

    @Test
    void aMemeThatWasWithdrawnMeanwhileIsNotLookedAt() {
        when(reads.handle(memeId)).thenReturn(view("RETIRED"));
        reported(List.of(report(ReportReason.INAPPROPRIATE, "")));

        runner().handle(memeId);

        verify(reviews).delete(memeId);
        verify(tagger, never()).reassess(any(), anyString(), any());
    }

    @Test
    void aFailureGoesBackInLineWithTheReason() {
        when(reads.handle(memeId)).thenReturn(view("APPROVED"));
        reported(List.of(report(ReportReason.WRONG_TAGS, "x")));
        when(storage.get(anyString())).thenReturn(new byte[] {1});
        when(tagger.reassess(any(), anyString(), any())).thenThrow(new LlmUnavailableException("model is down"));

        runner().handle(memeId);

        verify(reviews).fail(memeId, "model is down", 3);
        verify(reviews, never()).complete(any(), any());
    }

    // -- the rules act on their own ------------------------------------------------

    private List<Report> reporters(int count, ReportReason reason) {
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
        return new ResolveReportsService(cases, reviews, revise, restore, clock);
    }

    @Test
    void adoptingTheProposalHandsItToCurationAndClosesTheReports() {
        reported(List.of(report(ReportReason.WRONG_TAGS, "x"), report(ReportReason.WRONG_MEANING, "y")));
        when(reviews.find(memeId)).thenReturn(Optional.of(new ReviewState("DONE", suggestion(), null, false)));

        resolving().apply(memeId);

        verify(revise).handle(new ReviseProfileFromProposalUseCase.Input(memeId, "新的意思", List.of("新情境"),
                List.of("得意"), "新文字", List.of("新標籤"), false));
        assertThat(cases.get(memeId).openReports()).isEmpty();
        assertThat(cases.get(memeId).reports()).hasSize(2).allSatisfy(r -> {
            assertThat(r.closing().resolution()).isEqualTo(Resolution.APPLIED);
            assertThat(r.closing().automatic()).isFalse();
            assertThat(r.closing().note()).isEqualTo("因為圖裡其實是…");
        });
        verify(reviews).delete(memeId);
    }

    @Test
    void anAutomaticAdoptionIsMarkedAsSuchSoItCanBeTakenBack() {
        reported(List.of(report(ReportReason.WRONG_TAGS, "x")));
        when(reviews.find(memeId)).thenReturn(Optional.of(new ReviewState("DONE", suggestion(), null, false)));

        resolving().applyAutomatically(memeId);

        verify(revise).handle(org.mockito.ArgumentMatchers.argThat(input -> input.automatic()));
        assertThat(cases.get(memeId).reports()).singleElement()
                .satisfies(r -> assertThat(r.closing().automatic()).isTrue());
    }

    @Test
    void thereIsNothingToAdoptBeforeTheModelHasAnswered() {
        reported(List.of(report(ReportReason.WRONG_TAGS, "x")));
        when(reviews.find(memeId)).thenReturn(Optional.of(new ReviewState("RUNNING", null, null, false)));

        assertThatThrownBy(() -> resolving().apply(memeId)).isInstanceOf(DomainRuleViolation.class);
        assertThat(cases.get(memeId).openReports()).hasSize(1);
    }

    @Test
    void aProposalCurationRefusesIsNotAdopted() {
        reported(List.of(report(ReportReason.WRONG_TAGS, "x")));
        var empty = new Suggestion(false, "", List.of(), List.of(), List.of(), "", "不是梗圖");
        when(reviews.find(memeId)).thenReturn(Optional.of(new ReviewState("DONE", empty, null, false)));
        doThrow(new DomainRuleViolation("no")).when(revise).handle(any());

        assertThatThrownBy(() -> resolving().apply(memeId)).isInstanceOf(DomainRuleViolation.class);
        assertThat(cases.get(memeId).openReports()).hasSize(1);
    }

    @Test
    void dismissingClosesTheReportsAndLeavesTheMemeAlone() {
        reported(List.of(report(ReportReason.WRONG_TAGS, "x")));

        resolving().dismiss(memeId);

        assertThat(cases.get(memeId).reports()).singleElement().satisfies(r -> {
            assertThat(r.closing().resolution()).isEqualTo(Resolution.DISMISSED);
            assertThat(r.closing().automatic()).isFalse();
        });
        verify(reviews).delete(memeId);
        verify(revise, never()).handle(any());
    }

    @Test
    void lookingAgainNeedsAnOpenReportAndIgnoresTheBudget() {
        var handler = resolving();

        assertThatThrownBy(() -> handler.reanalyze(memeId)).isInstanceOf(DomainRuleViolation.class);

        reported(List.of(report(ReportReason.WRONG_TAGS, "x")));
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
        reported(List.of(dismissedByTheRules(user)));
        when(restore.handle(memeId)).thenThrow(new DomainRuleViolation("changed since"));

        assertThatThrownBy(() -> resolving().undoAutomatic(memeId)).isInstanceOf(DomainRuleViolation.class);
        assertThat(cases.get(memeId).openReports()).isEmpty();
    }

    private Report dismissedByTheRules(UUID by) {
        return new Report(UUID.randomUUID(), by, ReportReason.OTHER, "x", NOW.minus(Duration.ofDays(1)),
                new Report.Closing(Resolution.DISMISSED, true, "沒問題", NOW.minus(Duration.ofHours(2))));
    }

    @Test
    void automaticallyClosedReportsCanBeOpenedAgainAndTheModelLooksOnceMore() {
        reported(List.of(dismissedByTheRules(user), dismissedByTheRules(UUID.randomUUID())));
        when(restore.handle(memeId)).thenReturn(false);

        resolving().undoAutomatic(memeId);

        assertThat(cases.get(memeId).openReports()).hasSize(2);
        verify(reviews).request(memeId, true);
    }

    @Test
    void whenTheRulesDidNothingThereIsNothingToTakeBack() {
        when(restore.handle(memeId)).thenReturn(false);

        assertThatThrownBy(() -> resolving().undoAutomatic(memeId)).isInstanceOf(DomainRuleViolation.class);
    }

    // -- the administrator's list --------------------------------------------------

    private OpenReport openReport(ReportReason reason, String comment) {
        return new OpenReport(UUID.randomUUID(), memeId, UUID.randomUUID(), "someone", reason, comment, Instant.now());
    }

    private ListReportCasesService lister() {
        return new ListReportCasesService(view, reviews, reads, storage, restore, judge, clock);
    }

    @Test
    void theListPutsComplaintsAndProposalSideBySideWithTheirWeight() {
        when(view.open()).thenReturn(List.of(openReport(ReportReason.WRONG_TAGS, "x"), openReport(ReportReason.WRONG_MEANING, "y")));
        when(reads.handle(memeId)).thenReturn(view("APPROVED"));
        when(reviews.find(memeId)).thenReturn(Optional.of(new ReviewState("DONE", suggestion(), null, false)));
        when(storage.presignedGetUrl(anyString(), any())).thenReturn("https://storage/a");

        var listed = lister().handle();

        assertThat(listed).singleElement().satisfies(c -> {
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
        when(view.automaticSince(NOW.minus(ResolveReportsService.UNDO_WINDOW))).thenReturn(List.of(
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

    /** Keeps cases the way the database does: what is saved is a copy, not the object that was handed in. */
    private static final class FakeCases implements ReportCaseRepository {

        private final java.util.Map<UUID, ReportCase> stored = new java.util.HashMap<>();

        void put(ReportCase reportCase) {
            save(reportCase);
        }

        @Override
        public ReportCase get(UUID templateId) {
            ReportCase found = stored.get(templateId);
            return found == null ? ReportCase.empty(templateId) : ReportCase.restore(templateId, found.reports());
        }

        @Override
        public ReportCase getForUpdate(UUID templateId) {
            return get(templateId);
        }

        @Override
        public void save(ReportCase reportCase) {
            stored.put(reportCase.templateId(), ReportCase.restore(reportCase.templateId(), reportCase.reports()));
        }
    }
}

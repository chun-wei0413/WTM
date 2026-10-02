package com.wtm.application.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wtm.application.port.out.ReportPort;
import com.wtm.application.port.out.ReportPort.OpenReport;
import com.wtm.application.report.ReportJudge.Outcome;
import com.wtm.application.report.ReportPolicy.Standing;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportJudgeTest {

    private final ReportPort reports = mock(ReportPort.class);
    private final ReportPolicy policy = ReportPolicy.standard();
    private final ReportJudge judge = new ReportJudge(reports, policy);

    private OpenReport by(Standing standing, ReportReason reason) {
        UUID user = UUID.randomUUID();
        when(reports.standingOf(user)).thenReturn(standing);
        return new OpenReport(UUID.randomUUID(), UUID.randomUUID(), user, "u", reason, "", Instant.now());
    }

    private OpenReport newcomer(ReportReason reason) {
        return by(new Standing(false, 0, 0), reason);
    }

    private static Suggestion change() {
        return new Suggestion(true, "新", List.of("a"), List.of(), List.of("t"), "", "改了", false);
    }

    private static Suggestion keep() {
        return new Suggestion(true, "舊", List.of("a"), List.of(), List.of("t"), "", "沒問題", true);
    }

    // -- how much a reporter counts ---------------------------------------------

    @Test
    void anOrdinaryNewcomerCountsForOne() {
        assertThat(policy.weightOf(new Standing(false, 0, 0))).isEqualTo(1);
        assertThat(policy.weightOf(new Standing(false, 1, 1))).isEqualTo(1);
    }

    @Test
    void aReporterWhoWasOftenRightCountsForMore() {
        assertThat(policy.weightOf(new Standing(false, 2, 1))).isEqualTo(policy.trustedWeight());
        assertThat(policy.weightOf(new Standing(false, 2, 2))).as("as often wrong as right").isEqualTo(1);
    }

    @Test
    void aReporterWhoWasOnlyEverWrongCountsForNothing() {
        assertThat(policy.weightOf(new Standing(false, 0, policy.lowTrustDismissals()))).isZero();
        assertThat(policy.weightOf(new Standing(false, 0, policy.lowTrustDismissals() - 1))).isEqualTo(1);
        assertThat(policy.weightOf(new Standing(false, 1, 50))).as("one success gives a second chance").isEqualTo(1);
    }

    @Test
    void anAdministratorGetsTheModelsLookButNotMore() {
        assertThat(policy.weightOf(new Standing(true, 0, 0))).isEqualTo(policy.analyzeAtWeight());
        assertThat(policy.weightOf(new Standing(true, 0, 0))).isLessThan(policy.autoApplyAtWeight());
    }

    @Test
    void thePeopleAreCountedOnceEach() {
        UUID user = UUID.randomUUID();
        when(reports.standingOf(user)).thenReturn(new Standing(false, 0, 0));
        var one = new OpenReport(UUID.randomUUID(), UUID.randomUUID(), user, "u", ReportReason.OTHER, "", Instant.now());

        assertThat(judge.weightOf(List.of(one, one, one))).isEqualTo(1);
    }

    // -- what to do with the model's answer ----------------------------------------

    @Test
    void nothingWrongAndFewInsistingClosesTheReports() {
        var open = List.of(newcomer(ReportReason.WRONG_TAGS), newcomer(ReportReason.WRONG_MEANING));

        assertThat(judge.decide(open, keep())).isEqualTo(Outcome.AUTO_DISMISS);
    }

    @Test
    void nothingWrongButManyInsistingIsLeftToAPerson() {
        var open = List.of(newcomer(ReportReason.WRONG_TAGS), newcomer(ReportReason.WRONG_TAGS),
                newcomer(ReportReason.WRONG_TAGS));

        assertThat(judge.decide(open, keep())).isEqualTo(Outcome.NEEDS_ADMINISTRATOR);
    }

    @Test
    void aChangeWithEnoughAgreementIsAdopted() {
        var open = List.of(newcomer(ReportReason.WRONG_TAGS), newcomer(ReportReason.WRONG_TAGS),
                newcomer(ReportReason.WRONG_MEANING));

        assertThat(judge.decide(open, change())).isEqualTo(Outcome.AUTO_APPLY);
    }

    @Test
    void aTrustedReporterPlusOneOtherIsEnough() {
        var open = List.of(by(new Standing(false, 3, 0), ReportReason.WRONG_TAGS), newcomer(ReportReason.WRONG_TAGS));

        assertThat(judge.decide(open, change())).isEqualTo(Outcome.AUTO_APPLY);
    }

    @Test
    void aChangeWithoutEnoughAgreementIsLeftToAPerson() {
        var open = List.of(newcomer(ReportReason.WRONG_TAGS), newcomer(ReportReason.WRONG_TAGS));

        assertThat(judge.decide(open, change())).isEqualTo(Outcome.NEEDS_ADMINISTRATOR);
    }

    @Test
    void anAdministratorsReportCountsTowardsAgreementLikeAnyoneElses() {
        var open = List.of(by(new Standing(true, 0, 0), ReportReason.WRONG_TAGS), newcomer(ReportReason.WRONG_TAGS));

        assertThat(judge.decide(open, change())).as("2 + 1 reaches 3").isEqualTo(Outcome.AUTO_APPLY);
    }

    @Test
    void anythingAboutWhetherThePictureBelongsIsLeftToAPerson() {
        var open = List.of(newcomer(ReportReason.INAPPROPRIATE), newcomer(ReportReason.WRONG_TAGS),
                newcomer(ReportReason.WRONG_TAGS));

        assertThat(judge.decide(open, change())).isEqualTo(Outcome.NEEDS_ADMINISTRATOR);
        assertThat(judge.decide(open, keep())).isEqualTo(Outcome.NEEDS_ADMINISTRATOR);
    }

    @Test
    void aModelThatCallsItNotAMemeIsLeftToAPerson() {
        var open = List.of(newcomer(ReportReason.WRONG_TAGS), newcomer(ReportReason.WRONG_TAGS),
                newcomer(ReportReason.WRONG_TAGS));
        var notAMeme = new Suggestion(false, "", List.of(), List.of(), List.of(), "", "風景照", false);

        assertThat(judge.decide(open, notAMeme)).isEqualTo(Outcome.NEEDS_ADMINISTRATOR);
    }

    @Test
    void aProposalThatCouldNotReplaceTheDescriptionIsNotAdopted() {
        var open = List.of(newcomer(ReportReason.WRONG_TAGS), newcomer(ReportReason.WRONG_TAGS),
                newcomer(ReportReason.WRONG_TAGS));
        var incomplete = new Suggestion(true, "有意思但沒有情境", List.of(), List.of(), List.of(), "", "x", false);

        assertThat(judge.decide(open, incomplete)).isEqualTo(Outcome.NEEDS_ADMINISTRATOR);
    }

    @Test
    void reportersWorthNothingNeverBuyAnAnalysis() {
        var liars = List.of(by(new Standing(false, 0, 9), ReportReason.OTHER), by(new Standing(false, 0, 9), ReportReason.OTHER),
                by(new Standing(false, 0, 9), ReportReason.OTHER));

        assertThat(judge.worthAnalyzing(liars)).isFalse();
    }
}

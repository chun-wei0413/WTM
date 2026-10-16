package com.wtm.feedback.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wtm.feedback.entity.Report.Closing;
import com.wtm.shared.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

    private final UUID meme = UUID.randomUUID();
    private final UserId ann = UserId.newId();
    private final UserId bob = UserId.newId();
    private final ReportCase reportCase = ReportCase.empty(meme);

    @Test
    void aReportIsKeptWithItsReasonAndATrimmedComment() {
        reportCase.submit(ann, ReportReason.WRONG_TAGS, "  是狗不是貓 ", NOW);

        assertThat(reportCase.openReports()).singleElement().satisfies(r -> {
            assertThat(r.userId()).isEqualTo(ann);
            assertThat(r.reason()).isEqualTo(ReportReason.WRONG_TAGS);
            assertThat(r.comment()).isEqualTo("是狗不是貓");
            assertThat(r.createdAt()).isEqualTo(NOW);
        });
    }

    @Test
    void aSecondReportFromTheSamePersonReplacesTheFirst() {
        reportCase.submit(ann, ReportReason.WRONG_TAGS, "a", NOW);
        UUID first = reportCase.openReports().get(0).id();

        reportCase.submit(ann, ReportReason.OTHER, "b", NOW.plusSeconds(60));

        assertThat(reportCase.openReports()).singleElement().satisfies(r -> {
            assertThat(r.id()).isEqualTo(first);
            assertThat(r.reason()).isEqualTo(ReportReason.OTHER);
            assertThat(r.comment()).isEqualTo("b");
            assertThat(r.createdAt()).isEqualTo(NOW.plusSeconds(60));
        });
    }

    @Test
    void differentPeopleAreKeptApart() {
        reportCase.submit(ann, ReportReason.OTHER, "a", NOW);
        reportCase.submit(bob, ReportReason.OTHER, "b", NOW.plusSeconds(1));

        assertThat(reportCase.openReports()).extracting(Report::userId).containsExactly(ann, bob);
    }

    @Test
    void aReasonIsRequiredAndTheCommentHasALimit() {
        assertThatThrownBy(() -> reportCase.submit(ann, null, "x", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reportCase.submit(ann, ReportReason.OTHER, "字".repeat(ReportCase.MAX_COMMENT_LENGTH + 1), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        reportCase.submit(ann, ReportReason.OTHER, "字".repeat(ReportCase.MAX_COMMENT_LENGTH), NOW);
        assertThat(reportCase.openReports()).hasSize(1);
    }

    @Test
    void closingTakesEveryOpenReportAtOnce() {
        reportCase.submit(ann, ReportReason.OTHER, "a", NOW);
        reportCase.submit(bob, ReportReason.OTHER, "b", NOW);

        int closed = reportCase.close(Resolution.APPLIED, false, "改了", NOW.plusSeconds(5));

        assertThat(closed).isEqualTo(2);
        assertThat(reportCase.hasOpenReports()).isFalse();
        assertThat(reportCase.reports()).allSatisfy(r -> {
            assertThat(r.closing().resolution()).isEqualTo(Resolution.APPLIED);
            assertThat(r.closing().automatic()).isFalse();
            assertThat(r.closing().note()).isEqualTo("改了");
            assertThat(r.closing().at()).isEqualTo(NOW.plusSeconds(5));
        });
    }

    @Test
    void closingTwiceDoesNotTouchTheReportsAlreadyClosed() {
        reportCase.submit(ann, ReportReason.OTHER, "a", NOW);
        reportCase.close(Resolution.DISMISSED, true, "沒問題", NOW);

        assertThat(reportCase.close(Resolution.APPLIED, false, null, NOW.plusSeconds(1))).isZero();
        assertThat(reportCase.reports().get(0).closing().resolution()).isEqualTo(Resolution.DISMISSED);
    }

    @Test
    void aPersonCanReportAgainAfterTheirReportWasClosed() {
        reportCase.submit(ann, ReportReason.OTHER, "a", NOW);
        reportCase.close(Resolution.DISMISSED, false, null, NOW);

        reportCase.submit(ann, ReportReason.WRONG_TAGS, "again", NOW.plusSeconds(60));

        assertThat(reportCase.reports()).hasSize(2);
        assertThat(reportCase.openReports()).singleElement().satisfies(r -> assertThat(r.comment()).isEqualTo("again"));
    }

    // -- taking the rules' dismissal back ---------------------------------------------

    @Test
    void whatTheRulesDismissedRecentlyIsOpenedAgain() {
        reportCase.submit(ann, ReportReason.OTHER, "a", NOW);
        reportCase.submit(bob, ReportReason.OTHER, "b", NOW);
        reportCase.close(Resolution.DISMISSED, true, "沒問題", NOW);

        int reopened = reportCase.reopenAutomaticallyDismissed(NOW.minus(Duration.ofDays(7)));

        assertThat(reopened).isEqualTo(2);
        assertThat(reportCase.openReports()).hasSize(2).allSatisfy(r -> assertThat(r.closing()).isNull());
    }

    @Test
    void whatAnAdministratorDismissedOrTheRulesAdoptedStaysClosed() {
        reportCase.submit(ann, ReportReason.OTHER, "a", NOW);
        reportCase.close(Resolution.DISMISSED, false, null, NOW);
        reportCase.submit(bob, ReportReason.OTHER, "b", NOW);
        reportCase.close(Resolution.APPLIED, true, "改了", NOW);

        assertThat(reportCase.reopenAutomaticallyDismissed(NOW.minus(Duration.ofDays(7)))).isZero();
    }

    @Test
    void whatWasDismissedLongAgoStaysClosed() {
        reportCase.submit(ann, ReportReason.OTHER, "a", NOW);
        reportCase.close(Resolution.DISMISSED, true, "沒問題", NOW);

        assertThat(reportCase.reopenAutomaticallyDismissed(NOW.plusSeconds(1))).isZero();
    }

    @Test
    void aPersonWhoReportedAgainKeepsTheirNewReportAndTheOldOneStaysClosed() {
        reportCase.submit(ann, ReportReason.OTHER, "old", NOW);
        reportCase.close(Resolution.DISMISSED, true, "沒問題", NOW);
        reportCase.submit(ann, ReportReason.WRONG_TAGS, "new", NOW.plusSeconds(60));

        assertThat(reportCase.reopenAutomaticallyDismissed(NOW.minus(Duration.ofDays(7)))).isZero();
        assertThat(reportCase.openReports()).singleElement().satisfies(r -> assertThat(r.comment()).isEqualTo("new"));
    }

    @Test
    void aPersonWithTwoDismissedReportsGetsOnlyTheLatestBack() {
        UUID old = UUID.randomUUID();
        UUID recent = UUID.randomUUID();
        var closing = new Closing(Resolution.DISMISSED, true, "x", NOW);
        var restored = ReportCase.restore(meme, List.of(
                new Report(old, ann, ReportReason.OTHER, "old", NOW.minusSeconds(3600), closing),
                new Report(recent, ann, ReportReason.OTHER, "recent", NOW.minusSeconds(60),
                        new Closing(Resolution.DISMISSED, true, "x", NOW.plusSeconds(1)))));

        assertThat(restored.reopenAutomaticallyDismissed(NOW.minus(Duration.ofDays(7)))).isEqualTo(1);
        assertThat(restored.openReports()).singleElement().satisfies(r -> assertThat(r.id()).isEqualTo(recent));
    }
}

package com.wtm.feedback.usecase.service;

import com.wtm.feedback.entity.ReportPolicy;
import com.wtm.feedback.entity.Report;
import com.wtm.feedback.entity.ReportCase;
import com.wtm.feedback.usecase.port.out.report.ReporterPort;
import com.wtm.shared.port.Suggestion;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Decides how much a group of reports counts for, and what to do with them once the vision model has
 * given its opinion: close them, adopt the proposal, or leave the decision to an administrator.
 */
public class ReportJudge {

    public enum Outcome {
        /** The model found nothing wrong and not many people insist: close the reports. */
        AUTO_DISMISS,
        /** Enough people agree and the model proposes a usable new description: adopt it. */
        AUTO_APPLY,
        NEEDS_ADMINISTRATOR
    }

    private final ReporterPort reporters;
    private final ReportPolicy policy;

    public ReportJudge(ReporterPort reporters, ReportPolicy policy) {
        this.reporters = reporters;
        this.policy = policy;
    }

    public ReportPolicy policy() {
        return policy;
    }

    /** The combined weight of these people, each counted once. */
    public int weightOf(Collection<UUID> people) {
        return people.stream().distinct().mapToInt(user -> policy.weightOf(reporters.standingOf(user))).sum();
    }

    /** The weight of the people who have an open report about the meme. */
    public int weightOf(ReportCase reportCase) {
        return weightOf(reportCase.openReports().stream().map(Report::userId).toList());
    }

    /** Whether the reports are worth spending the model's time on without being asked by an administrator. */
    public boolean worthAnalyzing(ReportCase reportCase) {
        return weightOf(reportCase) >= policy.analyzeAtWeight();
    }

    public Outcome decide(ReportCase reportCase, Suggestion suggestion) {
        List<Report> open = reportCase.openReports();
        // Anything about whether a picture belongs in the library at all is a person's call.
        if (open.isEmpty() || open.stream().anyMatch(r -> r.reason().questionsWhetherItBelongs())
                || !suggestion.isMeme()) {
            return Outcome.NEEDS_ADMINISTRATOR;
        }
        boolean agreed = weightOf(reportCase) >= policy.autoApplyAtWeight();
        if (suggestion.keep()) {
            // Several people insisting against the model's answer is exactly what a person should look at.
            return agreed ? Outcome.NEEDS_ADMINISTRATOR : Outcome.AUTO_DISMISS;
        }
        return agreed && suggestion.isUsableDescription() ? Outcome.AUTO_APPLY : Outcome.NEEDS_ADMINISTRATOR;
    }
}

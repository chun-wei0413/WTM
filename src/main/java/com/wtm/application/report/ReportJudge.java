package com.wtm.application.report;

import com.wtm.application.port.out.ReportPort;
import com.wtm.application.port.out.ReportPort.OpenReport;
import java.util.List;

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

    private final ReportPort reports;
    private final ReportPolicy policy;

    public ReportJudge(ReportPort reports, ReportPolicy policy) {
        this.reports = reports;
        this.policy = policy;
    }

    public ReportPolicy policy() {
        return policy;
    }

    /** The reporters' combined weight, each person counted once. */
    public int weightOf(List<OpenReport> open) {
        return open.stream().map(OpenReport::userId).distinct()
                .mapToInt(user -> policy.weightOf(reports.standingOf(user))).sum();
    }

    /** Whether the reports are worth spending the model's time on without being asked by an administrator. */
    public boolean worthAnalyzing(List<OpenReport> open) {
        return weightOf(open) >= policy.analyzeAtWeight();
    }

    public Outcome decide(List<OpenReport> open, Suggestion suggestion) {
        // Anything about whether a picture belongs in the library at all is a person's call.
        if (open.isEmpty() || open.stream().anyMatch(r -> r.reason() == ReportReason.INAPPROPRIATE)
                || !suggestion.isMeme()) {
            return Outcome.NEEDS_ADMINISTRATOR;
        }
        boolean agreed = weightOf(open) >= policy.autoApplyAtWeight();
        if (suggestion.keep()) {
            // Several people insisting against the model's answer is exactly what a person should look at.
            return agreed ? Outcome.NEEDS_ADMINISTRATOR : Outcome.AUTO_DISMISS;
        }
        return agreed && suggestion.isUsableDescription() ? Outcome.AUTO_APPLY : Outcome.NEEDS_ADMINISTRATOR;
    }
}

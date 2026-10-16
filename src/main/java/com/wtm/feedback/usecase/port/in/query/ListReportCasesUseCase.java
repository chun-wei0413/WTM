package com.wtm.feedback.usecase.port.in.query;

import com.wtm.shared.MemeProfile;
import com.wtm.shared.port.Suggestion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ListReportCasesUseCase {

    /**
     * @param review the model's side of the case; null when it has not been asked
     * @param weight how much the reports count for together
     * @param neededWeight what they need to count for before the model is asked on its own
     */
    record ReportCase(UUID templateId, String name, String status, String imageUrl, MemeProfile current,
                             List<OpenReport> reports, Review review, int weight, int neededWeight) {
    }

    /** @param status PENDING, RUNNING, DONE or FAILED */
    record Review(String status, Suggestion suggestion, String error) {
    }

    /**
     * @param action APPLIED (the model's proposal was adopted) or DISMISSED (nothing was changed)
     * @param note the model's reasoning
     */
    record AutomaticEntry(UUID templateId, String name, String imageUrl, String action, int reports,
                                 String note, Instant at, boolean canUndo) {
    }

    List<ReportCase> handle();

    /** What the rules closed on their own in the last week, newest first. */
    List<AutomaticEntry> automatic();
}

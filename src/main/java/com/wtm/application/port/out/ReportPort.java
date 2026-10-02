package com.wtm.application.port.out;

import com.wtm.application.report.ReportPolicy.Standing;
import com.wtm.application.report.ReportReason;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reports people made about library memes.
 */
public interface ReportPort {

    /**
     * Records the report, or replaces what the same person wrote before about the same meme.
     *
     * @return false when there is no published meme with that id
     */
    boolean submit(UUID userId, UUID templateId, ReportReason reason, String comment);

    /** How many reports the person has open at the moment. */
    int openCountOf(UUID userId);

    /**
     * How many different memes other than {@code except} the person reported (or changed their report about)
     * since the given time. The meme being reported now is left out so that changing what one already said
     * about it is never refused.
     */
    int reportedSince(UUID userId, Instant since, UUID except);

    /** What the person's earlier reports came to; used to decide how much their next one counts for. */
    Standing standingOf(UUID userId);

    /** Every open report, oldest first. */
    List<OpenReport> open();

    /** The open reports about one meme, oldest first. */
    List<OpenReport> openAbout(UUID templateId);

    /**
     * Closes the open reports about a meme.
     *
     * @param automatic true when the rules closed them, false when an administrator did
     * @param note why, in the vision model's words when it was the model's proposal that decided
     */
    void resolve(UUID templateId, Resolution resolution, boolean automatic, String note);

    /**
     * Opens again the reports the rules closed as "nothing wrong", except where the person has since reported
     * the same meme again.
     *
     * @return how many were opened
     */
    int reopenAutomaticallyDismissed(UUID templateId, Instant closedSince);

    /** What the rules closed on their own since the given time, newest first, one entry per meme and outcome. */
    List<AutomaticAction> automaticSince(Instant since);

    enum Resolution {
        /** The proposed description was adopted. */
        APPLIED,
        DISMISSED
    }

    record OpenReport(UUID id, UUID templateId, UUID userId, String username, ReportReason reason, String comment,
                      Instant createdAt) {
    }

    record AutomaticAction(UUID templateId, Resolution resolution, int reports, String note, Instant at) {
    }
}

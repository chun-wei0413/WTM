package com.wtm.application.port.out;

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

    /** Every open report, oldest first. */
    List<OpenReport> open();

    /** The open reports about one meme, oldest first. */
    List<OpenReport> openAbout(UUID templateId);

    /** Closes the open reports about a meme with how the administrator dealt with them. */
    void resolve(UUID templateId, Resolution resolution);

    enum Resolution {
        /** The proposed description was adopted. */
        APPLIED,
        DISMISSED
    }

    record OpenReport(UUID id, UUID templateId, String username, ReportReason reason, String comment,
                      Instant createdAt) {
    }
}

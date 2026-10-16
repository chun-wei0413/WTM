package com.wtm.feedback.usecase.port.out.report;

import com.wtm.feedback.entity.ReportPolicy.Standing;
import com.wtm.shared.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * What is known about a person as a reporter, across all the memes they have reported. No single
 * {@code ReportCase} can answer these.
 */
public interface ReporterPort {

    /** How many reports the person has open at the moment. */
    int openCountOf(UserId userId);

    /**
     * How many different memes other than {@code except} the person reported (or changed their report about)
     * since the given time. The meme being reported now is left out so that changing what one already said
     * about it is never refused.
     */
    int reportedSince(UserId userId, Instant since, UUID except);

    /** What the person's earlier reports came to; used to decide how much their next one counts for. */
    Standing standingOf(UserId userId);
}

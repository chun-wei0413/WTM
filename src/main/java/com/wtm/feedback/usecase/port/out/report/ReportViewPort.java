package com.wtm.feedback.usecase.port.out.report;

import com.wtm.feedback.entity.Resolution;
import com.wtm.feedback.usecase.port.in.query.OpenReport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the administrator's list shows: reports with the names of the people who made them, and what the rules
 * decided on their own. Reads only; it bypasses the aggregate on purpose.
 */
public interface ReportViewPort {

    /** Every open report, oldest first. */
    List<OpenReport> open();

    /** What the rules closed on their own since the given time, newest first, one entry per meme and outcome. */
    List<AutomaticAction> automaticSince(Instant since);

    record AutomaticAction(UUID templateId, Resolution resolution, int reports, String note, Instant at) {
    }
}

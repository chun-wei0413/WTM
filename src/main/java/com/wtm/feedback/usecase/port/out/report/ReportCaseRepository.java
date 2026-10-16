package com.wtm.feedback.usecase.port.out.report;

import com.wtm.feedback.entity.ReportCase;
import java.util.UUID;

/**
 * Where the reports about each meme are kept, one {@link ReportCase} per meme.
 */
public interface ReportCaseRepository {

    /** The meme's case, empty when nobody has reported it. */
    ReportCase get(UUID templateId);

    /**
     * The same, and nobody else can change the meme's reports until the transaction ends, so that two people
     * reporting at once, or a report arriving while an administrator decides, are dealt with one after the other.
     */
    ReportCase getForUpdate(UUID templateId);

    void save(ReportCase reportCase);
}

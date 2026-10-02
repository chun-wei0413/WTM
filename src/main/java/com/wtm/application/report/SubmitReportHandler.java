package com.wtm.application.report;

import com.wtm.application.TemplateNotFoundException;
import com.wtm.application.TooManyRequestsException;
import com.wtm.application.port.out.ReportPort;
import com.wtm.application.port.out.ReviewPort;
import com.wtm.domain.template.TemplateId;
import java.time.Duration;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Takes a report that a meme's description does not fit, and asks the vision model to look at the meme
 * again so the administrator has a proposal to judge, not only a complaint.
 */
public class SubmitReportHandler {

    static final int MAX_COMMENT_LENGTH = 300;
    /** Each report can cost a minute of the graphics card, so one person may only have this many waiting. */
    static final int MAX_OPEN_PER_USER = 30;

    private final ReportPort reports;
    private final ReviewPort reviews;

    public SubmitReportHandler(ReportPort reports, ReviewPort reviews) {
        this.reports = reports;
        this.reviews = reviews;
    }

    /**
     * @throws IllegalArgumentException for a missing reason or a comment that is too long
     * @throws TemplateNotFoundException when there is no published meme with that id
     * @throws TooManyRequestsException when the person already has too many reports waiting
     */
    @Transactional
    public void handle(UUID userId, UUID templateId, ReportReason reason, String comment) {
        if (reason == null) {
            throw new IllegalArgumentException("A reason is required");
        }
        String text = comment == null ? "" : comment.strip();
        if (text.codePointCount(0, text.length()) > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("The comment must be at most " + MAX_COMMENT_LENGTH + " characters");
        }
        if (reports.openCountOf(userId) >= MAX_OPEN_PER_USER) {
            throw new TooManyRequestsException("You have too many reports waiting to be looked at", Duration.ofHours(1));
        }
        if (!reports.submit(userId, templateId, reason, text)) {
            throw new TemplateNotFoundException(new TemplateId(templateId));
        }
        reviews.request(templateId);
    }
}

package com.wtm.application.report;

import com.wtm.application.TemplateNotFoundException;
import com.wtm.application.TooManyRequestsException;
import com.wtm.application.port.out.ReportPort;
import com.wtm.application.port.out.ReportPort.OpenReport;
import com.wtm.application.port.out.ReviewPort;
import com.wtm.domain.template.TemplateId;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Takes a report that a meme's description does not fit. Recording a report is cheap, so it is always
 * done; asking the vision model to look again is not, so that only happens when enough trusted people
 * reported the meme, the meme was not looked at recently, and (when the model gets to it) the day's
 * budget is not used up.
 */
public class SubmitReportHandler {

    static final int MAX_COMMENT_LENGTH = 300;
    /** Each report can cost a minute of the graphics card, so one person may only have this many waiting. */
    static final int MAX_OPEN_PER_USER = 30;

    private final ReportPort reports;
    private final ReviewPort reviews;
    private final ReportJudge judge;
    private final ResolveReportsHandler resolver;
    private final Clock clock;

    public SubmitReportHandler(ReportPort reports, ReviewPort reviews, ReportJudge judge,
                               ResolveReportsHandler resolver, Clock clock) {
        this.reports = reports;
        this.reviews = reviews;
        this.judge = judge;
        this.resolver = resolver;
        this.clock = clock;
    }

    /**
     * @throws IllegalArgumentException for a missing reason or a comment that is too long
     * @throws TemplateNotFoundException when there is no published meme with that id
     * @throws TooManyRequestsException when the person has reported too much today or has too many reports waiting
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
        var policy = judge.policy();
        if (reports.reportedSince(userId, clock.instant().minus(Duration.ofHours(24)), templateId) >= policy.dailyReportsPerUser()) {
            throw new TooManyRequestsException("You have reported a lot of memes today; please try again tomorrow",
                    Duration.ofHours(1));
        }
        if (!reports.submit(userId, templateId, reason, text)) {
            throw new TemplateNotFoundException(new TemplateId(templateId));
        }
        askTheModelIfWorthIt(templateId);
    }

    private void askTheModelIfWorthIt(UUID templateId) {
        List<OpenReport> open = reports.openAbout(templateId);
        if (!judge.worthAnalyzing(open)) {
            return;   // recorded for the administrator, but not worth the model's time yet
        }
        // The model already looked, and with this report the people agreeing now reach the point where its
        // proposal is adopted: that needs no new look, only a new judgement of the proposal it already made.
        var existing = reviews.find(templateId);
        if (existing.isPresent() && !existing.get().forced() && existing.get().suggestion() != null
                && "DONE".equals(existing.get().status())
                && judge.decide(open, existing.get().suggestion()) == ReportJudge.Outcome.AUTO_APPLY) {
            resolver.applyAutomatically(templateId);
            return;
        }
        var lastLook = reviews.lastRunAt(templateId);
        if (lastLook.isPresent() && lastLook.get().isAfter(clock.instant().minus(judge.policy().cooldownPerMeme()))) {
            return;   // looked at recently: a new report, or a changed one, does not buy another look
        }
        reviews.request(templateId, false);
    }
}

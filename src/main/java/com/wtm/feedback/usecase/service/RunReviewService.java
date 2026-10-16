package com.wtm.feedback.usecase.service;

import com.wtm.curation.usecase.port.in.query.GetTemplateUseCase;
import com.wtm.curation.usecase.port.in.query.TemplateView;
import com.wtm.feedback.usecase.port.in.command.RunReviewUseCase;
import com.wtm.feedback.usecase.port.out.report.ReportPort;
import com.wtm.feedback.usecase.port.out.report.ReportPort.OpenReport;
import com.wtm.feedback.usecase.port.out.review.ReviewPort;
import com.wtm.shared.ImageTypes;
import com.wtm.shared.TemplateNotFoundException;
import com.wtm.shared.port.ObjectStoragePort;
import com.wtm.shared.port.ReviewRequest;
import com.wtm.shared.port.Suggestion;
import com.wtm.shared.port.VisionTaggerPort;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Has the vision model look at one reported meme again, with the complaints in hand, and keeps its
 * proposal. When the reports clearly call for it, the proposal is acted on without an administrator.
 * A failure puts the meme back in line, up to a limit.
 */
public class RunReviewService implements RunReviewUseCase {

    private static final Logger log = LoggerFactory.getLogger(RunReviewService.class);

    private final GetTemplateUseCase templates;
    private final ObjectStoragePort storage;
    private final VisionTaggerPort tagger;
    private final ReportPort reports;
    private final ReviewPort reviews;
    private final ReportJudge judge;
    private final ResolveReportsService resolver;
    private final int maxAttempts;

    public RunReviewService(GetTemplateUseCase templates, ObjectStoragePort storage, VisionTaggerPort tagger,
                            ReportPort reports, ReviewPort reviews, ReportJudge judge,
                            ResolveReportsService resolver, int maxAttempts) {
        this.templates = templates;
        this.storage = storage;
        this.tagger = tagger;
        this.reports = reports;
        this.reviews = reviews;
        this.judge = judge;
        this.resolver = resolver;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public void handle(UUID templateId) {
        try {
            TemplateView template = find(templateId);
            List<OpenReport> open = reports.openAbout(templateId);
            if (template == null || !"APPROVED".equals(template.status()) || open.isEmpty()) {
                reviews.delete(templateId);   // gone, withdrawn or already dealt with: nothing left to look at
                return;
            }
            boolean askedByAdministrator = reviews.find(templateId).map(state -> state.forced()).orElse(false);
            byte[] image = storage.get(template.imageKey());
            Suggestion suggestion = tagger.reassess(image, ImageTypes.contentTypeOf(template.imageKey()),
                    new ReviewRequest(template.profile(), open.stream().map(RunReviewService::describe).toList()));
            reviews.complete(templateId, suggestion);
            if (!askedByAdministrator) {
                actOn(templateId, suggestion);   // what an administrator asked for is theirs to decide
            }
        } catch (AutomaticActionFailed e) {
            // The proposal is kept and the administrator decides; the failed shortcut is no reason to look again.
            log.warn("Could not act on the proposal for {} by itself: {}", templateId, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Looking again at {} failed: {}", templateId, e.getMessage());
            reviews.fail(templateId, truncate(e.getMessage()), maxAttempts);
        }
    }

    private TemplateView find(UUID templateId) {
        try {
            return templates.handle(templateId);
        } catch (TemplateNotFoundException e) {
            return null;
        }
    }

    private void actOn(UUID templateId, Suggestion suggestion) {
        try {
            // Judge by who has reported by now, not by who had when the model started: it takes a while.
            List<OpenReport> open = reports.openAbout(templateId);
            switch (judge.decide(open, suggestion)) {
                case AUTO_APPLY -> resolver.applyAutomatically(templateId);
                case AUTO_DISMISS -> resolver.dismissAutomatically(templateId, suggestion.reasoning());
                case NEEDS_ADMINISTRATOR -> { }
            }
        } catch (RuntimeException e) {
            throw new AutomaticActionFailed(e);
        }
    }

    private static final class AutomaticActionFailed extends RuntimeException {
        AutomaticActionFailed(Throwable cause) {
            super(cause.getMessage(), cause);
        }
    }

    /** One line per report, saying what kind of problem it was and what the person wrote. */
    static String describe(OpenReport report) {
        String label = switch (report.reason()) {
            case WRONG_TAGS -> "標籤不精確";
            case WRONG_MEANING -> "描述不貼切";
            case NOT_A_MEME -> "這不是梗圖";
            case INAPPROPRIATE -> "不適合放在圖庫";
            case OTHER -> "其他問題";
        };
        return report.comment().isBlank() ? label : label + ":" + report.comment();
    }

    private static String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}

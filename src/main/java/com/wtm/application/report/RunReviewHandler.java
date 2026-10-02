package com.wtm.application.report;

import com.wtm.application.collection.TagTemplateHandler;
import com.wtm.application.port.out.ObjectStoragePort;
import com.wtm.application.port.out.ReportPort;
import com.wtm.application.port.out.ReportPort.OpenReport;
import com.wtm.application.port.out.ReviewPort;
import com.wtm.application.port.out.TemplateReadPort;
import com.wtm.application.port.out.VisionTaggerPort;
import com.wtm.application.template.query.TemplateView;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Has the vision model look at one reported meme again, with the complaints in hand, and keeps its
 * proposal for the administrator. A failure puts the meme back in line, up to a limit.
 */
public class RunReviewHandler {

    private static final Logger log = LoggerFactory.getLogger(RunReviewHandler.class);

    private final TemplateReadPort templates;
    private final ObjectStoragePort storage;
    private final VisionTaggerPort tagger;
    private final ReportPort reports;
    private final ReviewPort reviews;
    private final int maxAttempts;

    public RunReviewHandler(TemplateReadPort templates, ObjectStoragePort storage, VisionTaggerPort tagger,
                            ReportPort reports, ReviewPort reviews, int maxAttempts) {
        this.templates = templates;
        this.storage = storage;
        this.tagger = tagger;
        this.reports = reports;
        this.reviews = reviews;
        this.maxAttempts = maxAttempts;
    }

    public void handle(UUID templateId) {
        try {
            TemplateView template = templates.findById(templateId).orElse(null);
            List<OpenReport> open = reports.openAbout(templateId);
            if (template == null || !"APPROVED".equals(template.status()) || open.isEmpty()) {
                reviews.delete(templateId);   // gone, withdrawn or already dealt with: nothing left to look at
                return;
            }
            byte[] image = storage.get(template.imageKey());
            Suggestion suggestion = tagger.reassess(image, TagTemplateHandler.contentTypeOf(template.imageKey()),
                    new ReviewRequest(template.profile(), open.stream().map(RunReviewHandler::describe).toList()));
            reviews.complete(templateId, suggestion);
        } catch (RuntimeException e) {
            log.warn("Looking again at {} failed: {}", templateId, e.getMessage());
            reviews.fail(templateId, truncate(e.getMessage()), maxAttempts);
        }
    }

    /** One line per report, saying what kind of problem it was and what the person wrote. */
    static String describe(OpenReport report) {
        String label = switch (report.reason()) {
            case WRONG_TAGS -> "標籤不精確";
            case WRONG_MEANING -> "描述不貼切";
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

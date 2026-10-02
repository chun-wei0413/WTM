package com.wtm.application.report;

import com.wtm.application.port.out.ObjectStoragePort;
import com.wtm.application.port.out.ReportPort;
import com.wtm.application.port.out.ReportPort.OpenReport;
import com.wtm.application.port.out.ReviewPort;
import com.wtm.application.port.out.ReviewPort.ReviewState;
import com.wtm.application.port.out.TemplateReadPort;
import com.wtm.application.template.query.TemplateView;
import com.wtm.domain.template.MemeProfile;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The reported memes the administrator has to decide about: the complaints, the current description and
 * the vision model's new proposal side by side.
 */
public class ListReportCasesHandler {

    private static final Duration IMAGE_URL_TTL = Duration.ofMinutes(15);

    private final ReportPort reports;
    private final ReviewPort reviews;
    private final TemplateReadPort templates;
    private final ObjectStoragePort storage;

    public ListReportCasesHandler(ReportPort reports, ReviewPort reviews, TemplateReadPort templates,
                                  ObjectStoragePort storage) {
        this.reports = reports;
        this.reviews = reviews;
        this.templates = templates;
        this.storage = storage;
    }

    public List<ReportCase> handle() {
        Map<UUID, List<OpenReport>> byMeme = new LinkedHashMap<>();
        for (OpenReport report : reports.open()) {
            byMeme.computeIfAbsent(report.templateId(), id -> new java.util.ArrayList<>()).add(report);
        }
        return byMeme.entrySet().stream()
                .flatMap(entry -> templates.findById(entry.getKey()).stream()
                        .map(t -> toCase(t, entry.getValue())))
                .toList();
    }

    private ReportCase toCase(TemplateView template, List<OpenReport> open) {
        ReviewState review = reviews.find(template.id()).orElse(null);
        return new ReportCase(template.id(), template.name(), template.status(),
                storage.presignedGetUrl(template.imageKey(), IMAGE_URL_TTL), template.profile(), open,
                review == null ? null : new Review(review.status(), review.suggestion(), review.error()));
    }

    /**
     * @param review the model's side of the case; null when it has not been asked
     */
    public record ReportCase(UUID templateId, String name, String status, String imageUrl, MemeProfile current,
                             List<OpenReport> reports, Review review) {
    }

    /** @param status PENDING, RUNNING, DONE or FAILED */
    public record Review(String status, Suggestion suggestion, String error) {
    }
}

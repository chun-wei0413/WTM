package com.wtm.feedback.usecase.service;

import com.wtm.curation.entity.MemeProfile;
import com.wtm.curation.usecase.port.in.query.TemplateView;
import com.wtm.curation.usecase.port.out.template.ProfileHistoryPort;
import com.wtm.curation.usecase.port.out.template.TemplateReadPort;
import com.wtm.feedback.usecase.port.in.query.ListReportCasesUseCase;
import com.wtm.feedback.usecase.port.out.report.ReportPort;
import com.wtm.feedback.usecase.port.out.report.ReportPort.AutomaticAction;
import com.wtm.feedback.usecase.port.out.report.ReportPort.OpenReport;
import com.wtm.feedback.usecase.port.out.report.ReportPort.Resolution;
import com.wtm.feedback.usecase.port.out.review.ReviewPort;
import com.wtm.feedback.usecase.port.out.review.ReviewPort.ReviewState;
import com.wtm.shared.port.ObjectStoragePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the administrator sees: the reported memes that need a decision, with the complaints, the current
 * description and the vision model's new proposal side by side, and what the rules decided without them.
 */
public class ListReportCasesService implements ListReportCasesUseCase {

    private static final Duration IMAGE_URL_TTL = Duration.ofMinutes(15);

    private final ReportPort reports;
    private final ReviewPort reviews;
    private final TemplateReadPort templates;
    private final ObjectStoragePort storage;
    private final ProfileHistoryPort history;
    private final ReportJudge judge;
    private final Clock clock;

    public ListReportCasesService(ReportPort reports, ReviewPort reviews, TemplateReadPort templates,
                                  ObjectStoragePort storage, ProfileHistoryPort history, ReportJudge judge,
                                  Clock clock) {
        this.reports = reports;
        this.reviews = reviews;
        this.templates = templates;
        this.storage = storage;
        this.history = history;
        this.judge = judge;
        this.clock = clock;
    }

    @Override
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

    /** What the rules closed on their own in the last week, newest first. */
    @Override
    public List<AutomaticEntry> automatic() {
        Instant since = clock.instant().minus(ResolveReportsService.UNDO_WINDOW);
        return reports.automaticSince(since).stream()
                .flatMap(action -> templates.findById(action.templateId()).stream().map(t -> toEntry(t, action)))
                .toList();
    }

    private ReportCase toCase(TemplateView template, List<OpenReport> open) {
        ReviewState review = reviews.find(template.id()).orElse(null);
        return new ReportCase(template.id(), template.name(), template.status(),
                storage.presignedGetUrl(template.imageKey(), IMAGE_URL_TTL), template.profile(), open,
                review == null ? null : new Review(review.status(), review.suggestion(), review.error()),
                judge.weightOf(open), judge.policy().analyzeAtWeight());
    }

    private AutomaticEntry toEntry(TemplateView template, AutomaticAction action) {
        boolean canUndo = action.resolution() == Resolution.DISMISSED
                || history.latestAutomaticChange(template.id()).isPresent();
        return new AutomaticEntry(template.id(), template.name(),
                storage.presignedGetUrl(template.imageKey(), IMAGE_URL_TTL), action.resolution().name(),
                action.reports(), action.note(), action.at(), canUndo);
    }

}

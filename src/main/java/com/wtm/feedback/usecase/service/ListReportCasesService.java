package com.wtm.feedback.usecase.service;

import com.wtm.curation.usecase.port.in.command.RestoreProfileUseCase;
import com.wtm.curation.usecase.port.in.query.GetTemplateUseCase;
import com.wtm.curation.usecase.port.in.query.TemplateView;
import com.wtm.feedback.entity.ReportCase;
import com.wtm.feedback.entity.Resolution;
import com.wtm.feedback.usecase.port.in.query.ListReportCasesUseCase;
import com.wtm.feedback.usecase.port.in.query.OpenReport;
import com.wtm.feedback.usecase.port.out.report.ReportViewPort;
import com.wtm.feedback.usecase.port.out.report.ReportViewPort.AutomaticAction;
import com.wtm.feedback.usecase.port.out.review.ReviewPort;
import com.wtm.feedback.usecase.port.out.review.ReviewPort.ReviewState;
import com.wtm.shared.TemplateNotFoundException;
import com.wtm.shared.UserId;
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

    private final ReportViewPort reports;
    private final ReviewPort reviews;
    private final GetTemplateUseCase templates;
    private final ObjectStoragePort storage;
    private final RestoreProfileUseCase restore;
    private final ReportJudge judge;
    private final Clock clock;

    public ListReportCasesService(ReportViewPort reports, ReviewPort reviews, GetTemplateUseCase templates,
                                  ObjectStoragePort storage, RestoreProfileUseCase restore, ReportJudge judge,
                                  Clock clock) {
        this.reports = reports;
        this.reviews = reviews;
        this.templates = templates;
        this.storage = storage;
        this.restore = restore;
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
                .flatMap(entry -> find(entry.getKey()).stream()
                        .map(t -> toCase(t, entry.getValue())))
                .toList();
    }

    /** What the rules closed on their own in the last week, newest first. */
    @Override
    public List<AutomaticEntry> automatic() {
        Instant since = clock.instant().minus(ResolveReportsService.UNDO_WINDOW);
        return reports.automaticSince(since).stream()
                .flatMap(action -> find(action.templateId()).stream().map(t -> toEntry(t, action)))
                .toList();
    }

    private java.util.Optional<TemplateView> find(UUID id) {
        try {
            return java.util.Optional.of(templates.handle(id));
        } catch (TemplateNotFoundException e) {
            return java.util.Optional.empty();
        }
    }

    private ReportCase toCase(TemplateView template, List<OpenReport> open) {
        ReviewState review = reviews.find(template.id()).orElse(null);
        return new ReportCase(template.id(), template.name(), template.status(),
                storage.presignedGetUrl(template.imageKey(), IMAGE_URL_TTL), template.profile(), open,
                review == null ? null : new Review(review.status(), review.suggestion(), review.error()),
                judge.weightOf(open.stream().map(report -> new UserId(report.userId())).toList()), judge.policy().analyzeAtWeight());
    }

    private AutomaticEntry toEntry(TemplateView template, AutomaticAction action) {
        boolean canUndo = action.resolution() == Resolution.DISMISSED
                || restore.canRestore(template.id());
        return new AutomaticEntry(template.id(), template.name(),
                storage.presignedGetUrl(template.imageKey(), IMAGE_URL_TTL), action.resolution().name(),
                action.reports(), action.note(), action.at(), canUndo);
    }

}

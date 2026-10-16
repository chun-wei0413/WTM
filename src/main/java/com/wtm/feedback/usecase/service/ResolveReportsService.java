package com.wtm.feedback.usecase.service;

import com.wtm.curation.usecase.port.in.command.RestoreProfileUseCase;
import com.wtm.curation.usecase.port.in.command.ReviseProfileFromProposalUseCase;
import com.wtm.feedback.entity.ReportCase;
import com.wtm.feedback.entity.Resolution;
import com.wtm.feedback.usecase.port.in.command.ResolveReportsUseCase;
import com.wtm.feedback.usecase.port.out.report.ReportCaseRepository;
import com.wtm.feedback.usecase.port.out.review.ReviewPort;
import com.wtm.shared.DomainRuleViolation;
import com.wtm.shared.port.Suggestion;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * What happens to a reported meme: the administrator adopts the model's proposal, sets the reports aside
 * or asks the model again, or the rules do the first two when the evidence is clear, and an administrator
 * can take such an automatic decision back.
 */
public class ResolveReportsService implements ResolveReportsUseCase {

    /** How long an automatic decision can be taken back. */
    public static final Duration UNDO_WINDOW = Duration.ofDays(7);

    private final ReportCaseRepository cases;
    private final ReviewPort reviews;
    private final ReviseProfileFromProposalUseCase revise;
    private final RestoreProfileUseCase restore;
    private final Clock clock;

    public ResolveReportsService(ReportCaseRepository cases, ReviewPort reviews, ReviseProfileFromProposalUseCase revise,
                                 RestoreProfileUseCase restore, Clock clock) {
        this.cases = cases;
        this.reviews = reviews;
        this.revise = revise;
        this.restore = restore;
        this.clock = clock;
    }

    /**
     * Writes the proposed description into the meme (keeping its other names) and closes its reports.
     *
     * @throws DomainRuleViolation when there is no finished proposal, or it is not a usable description
     */
    @Transactional
    @Override
    public void apply(UUID templateId) {
        adopt(templateId, false);
    }

    /** The same, decided by the rules because enough trusted people agreed with the model. */
    @Transactional
    @Override
    public void applyAutomatically(UUID templateId) {
        adopt(templateId, true);
    }

    /** Closes the reports without changing the meme. */
    @Transactional
    @Override
    public void dismiss(UUID templateId) {
        close(templateId, Resolution.DISMISSED, false, null);
        reviews.delete(templateId);
    }

    /** The same, decided by the rules because the model found nothing wrong and few people insisted. */
    @Transactional
    @Override
    public void dismissAutomatically(UUID templateId, String note) {
        close(templateId, Resolution.DISMISSED, true, note);
        reviews.delete(templateId);
    }

    /** Asks the vision model to look at the meme again, whatever the daily budget says. */
    @Transactional
    @Override
    public void reanalyze(UUID templateId) {
        if (!cases.get(templateId).hasOpenReports()) {
            throw new DomainRuleViolation("This meme has no open reports");
        }
        reviews.request(templateId, true);
    }

    /**
     * Takes back what the rules did to a meme: puts the earlier description back, or opens the reports they
     * closed again (and has the model look once more).
     *
     * @throws DomainRuleViolation when there is nothing to take back, or the description was changed since
     */
    @Transactional
    @Override
    public void undoAutomatic(UUID templateId) {
        boolean restored = restore.handle(templateId);
        ReportCase reportCase = cases.getForUpdate(templateId);
        int reopened = reportCase.reopenAutomaticallyDismissed(clock.instant().minus(UNDO_WINDOW));
        cases.save(reportCase);
        if (!restored && reopened == 0) {
            throw new DomainRuleViolation("There is nothing automatic to take back for this meme");
        }
        if (reopened > 0) {
            reviews.request(templateId, true);
        }
    }

    private void adopt(UUID templateId, boolean automatic) {
        Suggestion suggestion = reviews.find(templateId)
                .map(state -> state.suggestion())
                .orElseThrow(() -> new DomainRuleViolation("There is no proposal to adopt for this meme"));
        revise.handle(new ReviseProfileFromProposalUseCase.Input(templateId, suggestion.meaning(),
                suggestion.usageExamples(), suggestion.emotions(), suggestion.imageText(), suggestion.tags(), automatic));
        close(templateId, Resolution.APPLIED, automatic, suggestion.reasoning());
        reviews.delete(templateId);
    }

    private void close(UUID templateId, Resolution resolution, boolean automatic, String note) {
        ReportCase reportCase = cases.getForUpdate(templateId);
        reportCase.close(resolution, automatic, note, clock.instant());
        cases.save(reportCase);
    }
}

package com.wtm.application.report;

import com.wtm.application.port.out.ProfileHistoryPort;
import com.wtm.application.port.out.ProfileHistoryPort.Change;
import com.wtm.application.port.out.ProfileHistoryPort.Source;
import com.wtm.application.port.out.ReportPort;
import com.wtm.application.port.out.ReportPort.Resolution;
import com.wtm.application.port.out.ReviewPort;
import com.wtm.application.port.out.TemplateRepository;
import com.wtm.domain.DomainRuleViolation;
import com.wtm.domain.template.MemeProfile;
import com.wtm.domain.template.MemeTemplate;
import com.wtm.domain.template.TemplateId;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * What happens to a reported meme: the administrator adopts the model's proposal, sets the reports aside
 * or asks the model again, or the rules do the first two when the evidence is clear, and an administrator
 * can take such an automatic decision back.
 */
public class ResolveReportsHandler {

    /** How long an automatic decision can be taken back. */
    public static final Duration UNDO_WINDOW = Duration.ofDays(7);

    private final ReportPort reports;
    private final ReviewPort reviews;
    private final TemplateRepository templates;
    private final ProfileHistoryPort history;
    private final Clock clock;

    public ResolveReportsHandler(ReportPort reports, ReviewPort reviews, TemplateRepository templates,
                                 ProfileHistoryPort history, Clock clock) {
        this.reports = reports;
        this.reviews = reviews;
        this.templates = templates;
        this.history = history;
        this.clock = clock;
    }

    /**
     * Writes the proposed description into the meme (keeping its other names) and closes its reports.
     *
     * @throws DomainRuleViolation when there is no finished proposal, or it is not a usable description
     */
    @Transactional
    public void apply(UUID templateId) {
        adopt(templateId, false);
    }

    /** The same, decided by the rules because enough trusted people agreed with the model. */
    @Transactional
    public void applyAutomatically(UUID templateId) {
        adopt(templateId, true);
    }

    /** Closes the reports without changing the meme. */
    @Transactional
    public void dismiss(UUID templateId) {
        reports.resolve(templateId, Resolution.DISMISSED, false, null);
        reviews.delete(templateId);
    }

    /** The same, decided by the rules because the model found nothing wrong and few people insisted. */
    @Transactional
    public void dismissAutomatically(UUID templateId, String note) {
        reports.resolve(templateId, Resolution.DISMISSED, true, note);
        reviews.delete(templateId);
    }

    /** Asks the vision model to look at the meme again, whatever the daily budget says. */
    @Transactional
    public void reanalyze(UUID templateId) {
        if (reports.openAbout(templateId).isEmpty()) {
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
    public void undoAutomatic(UUID templateId) {
        boolean restored = false;
        Change change = history.latestAutomaticChange(templateId).orElse(null);
        if (change != null) {
            MemeTemplate template = templates.getForUpdate(new TemplateId(templateId));
            if (!template.profile().equals(change.after())) {
                throw new DomainRuleViolation("The description was changed again since, so it is not put back");
            }
            template.reviseProfile(change.before());
            templates.save(template);
            history.markUndone(change.id());
            restored = true;
        }
        int reopened = reports.reopenAutomaticallyDismissed(templateId, clock.instant().minus(UNDO_WINDOW));
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
        MemeTemplate template = templates.getForUpdate(new TemplateId(templateId));
        MemeProfile before = template.profile();
        MemeProfile after = new MemeProfile(suggestion.meaning(), suggestion.usageExamples(),
                suggestion.emotions(), before.aliases(), suggestion.imageText(), suggestion.tags());
        if (!after.isComplete()) {
            throw new DomainRuleViolation("The proposal is missing the meaning or a usage example");
        }
        template.reviseProfile(after);
        templates.save(template);
        history.record(templateId, before, after, automatic ? Source.AUTO : Source.ADMIN);
        reports.resolve(templateId, Resolution.APPLIED, automatic, suggestion.reasoning());
        reviews.delete(templateId);
    }
}

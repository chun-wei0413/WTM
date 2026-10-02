package com.wtm.application.report;

import com.wtm.application.port.out.ReportPort;
import com.wtm.application.port.out.ReportPort.Resolution;
import com.wtm.application.port.out.ReviewPort;
import com.wtm.application.port.out.TemplateRepository;
import com.wtm.domain.DomainRuleViolation;
import com.wtm.domain.template.MemeProfile;
import com.wtm.domain.template.MemeTemplate;
import com.wtm.domain.template.TemplateId;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the administrator does with a reported meme: adopt the model's proposal, set the reports aside,
 * or ask the model to look again.
 */
public class ResolveReportsHandler {

    private final ReportPort reports;
    private final ReviewPort reviews;
    private final TemplateRepository templates;

    public ResolveReportsHandler(ReportPort reports, ReviewPort reviews, TemplateRepository templates) {
        this.reports = reports;
        this.reviews = reviews;
        this.templates = templates;
    }

    /**
     * Writes the proposed description into the meme (keeping its other names) and closes its reports.
     *
     * @throws DomainRuleViolation when there is no finished proposal, or it is not a usable description
     */
    @Transactional
    public void apply(UUID templateId) {
        Suggestion suggestion = reviews.find(templateId)
                .map(state -> state.suggestion())
                .orElseThrow(() -> new DomainRuleViolation("There is no proposal to adopt for this meme"));
        MemeTemplate template = templates.getForUpdate(new TemplateId(templateId));
        MemeProfile profile = new MemeProfile(suggestion.meaning(), suggestion.usageExamples(),
                suggestion.emotions(), template.profile().aliases(), suggestion.imageText(), suggestion.tags());
        if (!profile.isComplete()) {
            throw new DomainRuleViolation("The proposal is missing the meaning or a usage example");
        }
        template.reviseProfile(profile);
        templates.save(template);
        reports.resolve(templateId, Resolution.APPLIED);
        reviews.delete(templateId);
    }

    /** Closes the reports without changing the meme. */
    @Transactional
    public void dismiss(UUID templateId) {
        reports.resolve(templateId, Resolution.DISMISSED);
        reviews.delete(templateId);
    }

    /** Asks the vision model to look at the meme again, for example after more people reported it. */
    @Transactional
    public void reanalyze(UUID templateId) {
        if (reports.openAbout(templateId).isEmpty()) {
            throw new DomainRuleViolation("This meme has no open reports");
        }
        reviews.request(templateId);
    }
}

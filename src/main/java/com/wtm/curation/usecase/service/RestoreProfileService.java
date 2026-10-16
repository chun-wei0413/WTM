package com.wtm.curation.usecase.service;

import com.wtm.curation.entity.MemeTemplate;
import com.wtm.curation.usecase.port.in.command.RestoreProfileUseCase;
import com.wtm.curation.usecase.port.out.template.ProfileHistoryPort;
import com.wtm.curation.usecase.port.out.template.ProfileHistoryPort.Change;
import com.wtm.curation.usecase.port.out.template.TemplateRepository;
import com.wtm.shared.DomainRuleViolation;
import com.wtm.shared.TemplateId;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class RestoreProfileService implements RestoreProfileUseCase {

    private final TemplateRepository templates;
    private final ProfileHistoryPort history;

    public RestoreProfileService(TemplateRepository templates, ProfileHistoryPort history) {
        this.templates = templates;
        this.history = history;
    }

    @Transactional
    @Override
    public boolean handle(UUID templateId) {
        Change change = history.latestAutomaticChange(templateId).orElse(null);
        if (change == null) {
            return false;
        }
        MemeTemplate template = templates.getForUpdate(new TemplateId(templateId));
        if (!template.profile().equals(change.after())) {
            throw new DomainRuleViolation("The description was changed again since, so it is not put back");
        }
        template.reviseProfile(change.before());
        templates.save(template);
        history.markUndone(change.id());
        return true;
    }

    @Override
    public boolean canRestore(UUID templateId) {
        return history.latestAutomaticChange(templateId).isPresent();
    }
}

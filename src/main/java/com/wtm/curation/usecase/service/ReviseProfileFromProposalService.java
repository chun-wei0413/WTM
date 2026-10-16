package com.wtm.curation.usecase.service;

import com.wtm.curation.entity.MemeTemplate;
import com.wtm.curation.usecase.port.in.command.ReviseProfileFromProposalUseCase;
import com.wtm.curation.usecase.port.out.template.ProfileHistoryPort;
import com.wtm.curation.usecase.port.out.template.ProfileHistoryPort.Source;
import com.wtm.curation.usecase.port.out.template.TemplateRepository;
import com.wtm.shared.DomainRuleViolation;
import com.wtm.shared.MemeProfile;
import com.wtm.shared.TemplateId;
import org.springframework.transaction.annotation.Transactional;

public class ReviseProfileFromProposalService implements ReviseProfileFromProposalUseCase {

    private final TemplateRepository templates;
    private final ProfileHistoryPort history;

    public ReviseProfileFromProposalService(TemplateRepository templates, ProfileHistoryPort history) {
        this.templates = templates;
        this.history = history;
    }

    @Transactional
    @Override
    public void handle(Input input) {
        MemeTemplate template = templates.getForUpdate(new TemplateId(input.templateId()));
        MemeProfile before = template.profile();
        MemeProfile after = new MemeProfile(input.meaning(), input.usageExamples(), input.emotions(),
                before.aliases(), input.imageText(), input.tags());
        if (!after.isComplete()) {
            throw new DomainRuleViolation("The proposal is missing the meaning or a usage example");
        }
        template.reviseProfile(after);
        templates.save(template);
        history.record(input.templateId(), before, after, input.automatic() ? Source.AUTO : Source.ADMIN);
    }
}

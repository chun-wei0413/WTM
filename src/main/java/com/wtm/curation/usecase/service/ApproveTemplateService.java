package com.wtm.curation.usecase.service;

import com.wtm.curation.entity.MemeTemplate;
import com.wtm.curation.usecase.port.in.command.ApproveTemplateUseCase;
import com.wtm.curation.usecase.port.out.template.TemplateRepository;
import com.wtm.shared.TemplateId;
import org.springframework.transaction.annotation.Transactional;

public class ApproveTemplateService implements ApproveTemplateUseCase {

    private final TemplateRepository templates;

    public ApproveTemplateService(TemplateRepository templates) {
        this.templates = templates;
    }

    @Transactional
    @Override
    public void handle(TemplateId id) {
        MemeTemplate template = templates.getForUpdate(id);
        template.approve();
        templates.save(template);
    }
}

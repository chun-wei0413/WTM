package com.wtm.curation.usecase.service;

import com.wtm.curation.entity.MemeTemplate;
import com.wtm.curation.usecase.port.in.command.RetireTemplateUseCase;
import com.wtm.curation.usecase.port.out.template.TemplateRepository;
import com.wtm.shared.TemplateId;
import org.springframework.transaction.annotation.Transactional;

public class RetireTemplateService implements RetireTemplateUseCase {

    private final TemplateRepository templates;

    public RetireTemplateService(TemplateRepository templates) {
        this.templates = templates;
    }

    @Transactional
    @Override
    public void handle(TemplateId id) {
        MemeTemplate template = templates.getForUpdate(id);
        template.retire();
        templates.save(template);
    }
}

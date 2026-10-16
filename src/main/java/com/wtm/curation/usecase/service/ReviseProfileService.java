package com.wtm.curation.usecase.service;

import com.wtm.curation.entity.MemeProfile;
import com.wtm.curation.entity.MemeTemplate;
import com.wtm.curation.usecase.port.in.command.ReviseProfileUseCase;
import com.wtm.curation.usecase.port.out.template.TemplateRepository;
import com.wtm.shared.TemplateId;
import org.springframework.transaction.annotation.Transactional;

public class ReviseProfileService implements ReviseProfileUseCase {

    private final TemplateRepository templates;

    public ReviseProfileService(TemplateRepository templates) {
        this.templates = templates;
    }

    @Transactional
    @Override
    public void handle(TemplateId id, MemeProfile profile) {
        MemeTemplate template = templates.getForUpdate(id);
        template.reviseProfile(profile);
        templates.save(template);
    }
}

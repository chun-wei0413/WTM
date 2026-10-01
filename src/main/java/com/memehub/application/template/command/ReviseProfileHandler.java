package com.memehub.application.template.command;

import com.memehub.application.port.out.TemplateRepository;
import com.memehub.domain.template.MemeProfile;
import com.memehub.domain.template.MemeTemplate;
import com.memehub.domain.template.TemplateId;
import org.springframework.transaction.annotation.Transactional;

public class ReviseProfileHandler {

    private final TemplateRepository templates;

    public ReviseProfileHandler(TemplateRepository templates) {
        this.templates = templates;
    }

    @Transactional
    public void handle(TemplateId id, MemeProfile profile) {
        MemeTemplate template = templates.getForUpdate(id);
        template.reviseProfile(profile);
        templates.save(template);
    }
}

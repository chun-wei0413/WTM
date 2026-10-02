package com.wtm.application.template.command;

import com.wtm.application.port.out.TemplateRepository;
import com.wtm.domain.template.MemeProfile;
import com.wtm.domain.template.MemeTemplate;
import com.wtm.domain.template.TemplateId;
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

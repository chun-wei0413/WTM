package com.wtm.application.template.command;

import com.wtm.application.port.out.TemplateRepository;
import com.wtm.domain.template.MemeTemplate;
import com.wtm.domain.template.TemplateId;
import org.springframework.transaction.annotation.Transactional;

public class RetireTemplateHandler {

    private final TemplateRepository templates;

    public RetireTemplateHandler(TemplateRepository templates) {
        this.templates = templates;
    }

    @Transactional
    public void handle(TemplateId id) {
        MemeTemplate template = templates.getForUpdate(id);
        template.retire();
        templates.save(template);
    }
}

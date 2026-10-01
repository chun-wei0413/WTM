package com.memehub.application.template.command;

import com.memehub.application.port.out.TemplateRepository;
import com.memehub.domain.template.MemeTemplate;
import com.memehub.domain.template.Slot;
import com.memehub.domain.template.TemplateId;
import org.springframework.transaction.annotation.Transactional;

public class RedefineSlotHandler {

    private final TemplateRepository templates;

    public RedefineSlotHandler(TemplateRepository templates) {
        this.templates = templates;
    }

    @Transactional
    public void handle(TemplateId id, Slot slot) {
        MemeTemplate template = templates.getForUpdate(id);
        template.redefineSlot(slot);
        templates.save(template);
    }
}

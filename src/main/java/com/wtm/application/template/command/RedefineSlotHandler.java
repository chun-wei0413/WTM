package com.wtm.application.template.command;

import com.wtm.application.port.out.TemplateRepository;
import com.wtm.domain.template.MemeTemplate;
import com.wtm.domain.template.Slot;
import com.wtm.domain.template.TemplateId;
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

package com.wtm.application.template.command;

import com.wtm.application.port.out.TemplateRepository;
import com.wtm.domain.template.MemeTemplate;
import com.wtm.domain.template.Slot;
import com.wtm.domain.template.TemplateId;
import org.springframework.transaction.annotation.Transactional;

public class DefineSlotHandler {

    private final TemplateRepository templates;

    public DefineSlotHandler(TemplateRepository templates) {
        this.templates = templates;
    }

    @Transactional
    public void handle(TemplateId id, Slot slot) {
        MemeTemplate template = templates.getForUpdate(id);
        template.defineSlot(slot);
        templates.save(template);
    }
}

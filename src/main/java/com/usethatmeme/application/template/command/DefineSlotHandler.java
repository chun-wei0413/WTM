package com.usethatmeme.application.template.command;

import com.usethatmeme.application.port.out.TemplateRepository;
import com.usethatmeme.domain.template.MemeTemplate;
import com.usethatmeme.domain.template.Slot;
import com.usethatmeme.domain.template.TemplateId;
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

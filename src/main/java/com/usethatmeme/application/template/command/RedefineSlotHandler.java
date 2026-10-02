package com.usethatmeme.application.template.command;

import com.usethatmeme.application.port.out.TemplateRepository;
import com.usethatmeme.domain.template.MemeTemplate;
import com.usethatmeme.domain.template.Slot;
import com.usethatmeme.domain.template.TemplateId;
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

package com.memehub.application.template.command;

import com.memehub.application.port.out.TemplateRepository;
import com.memehub.domain.template.MemeTemplate;
import com.memehub.domain.template.TemplateId;
import org.springframework.transaction.annotation.Transactional;

public class RemoveSlotHandler {

    private final TemplateRepository templates;

    public RemoveSlotHandler(TemplateRepository templates) {
        this.templates = templates;
    }

    @Transactional
    public void handle(TemplateId id, int slotNo) {
        MemeTemplate template = templates.getForUpdate(id);
        template.removeSlot(slotNo);
        templates.save(template);
    }
}

package com.wtm.curation.usecase.service;

import com.wtm.curation.entity.MemeTemplate;
import com.wtm.curation.usecase.port.in.command.RemoveSlotUseCase;
import com.wtm.curation.usecase.port.out.template.TemplateRepository;
import com.wtm.shared.TemplateId;
import org.springframework.transaction.annotation.Transactional;

public class RemoveSlotService implements RemoveSlotUseCase {

    private final TemplateRepository templates;

    public RemoveSlotService(TemplateRepository templates) {
        this.templates = templates;
    }

    @Transactional
    @Override
    public void handle(Input input) {
        TemplateId id = input.id();
        int slotNo = input.slotNo();
        MemeTemplate template = templates.getForUpdate(id);
        template.removeSlot(slotNo);
        templates.save(template);
    }
}

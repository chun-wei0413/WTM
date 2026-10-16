package com.wtm.curation.usecase.service;

import com.wtm.curation.entity.MemeTemplate;
import com.wtm.curation.usecase.port.in.command.DefineSlotUseCase;
import com.wtm.curation.usecase.port.out.template.TemplateRepository;
import com.wtm.shared.Slot;
import com.wtm.shared.TemplateId;
import org.springframework.transaction.annotation.Transactional;

public class DefineSlotService implements DefineSlotUseCase {

    private final TemplateRepository templates;

    public DefineSlotService(TemplateRepository templates) {
        this.templates = templates;
    }

    @Transactional
    @Override
    public void handle(Input input) {
        TemplateId id = input.id();
        Slot slot = input.slot();
        MemeTemplate template = templates.getForUpdate(id);
        template.defineSlot(slot);
        templates.save(template);
    }
}

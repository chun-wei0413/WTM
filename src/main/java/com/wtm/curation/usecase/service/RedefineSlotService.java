package com.wtm.curation.usecase.service;

import com.wtm.curation.entity.MemeTemplate;
import com.wtm.curation.usecase.port.in.command.RedefineSlotUseCase;
import com.wtm.curation.usecase.port.out.template.TemplateRepository;
import com.wtm.shared.Slot;
import com.wtm.shared.TemplateId;
import org.springframework.transaction.annotation.Transactional;

public class RedefineSlotService implements RedefineSlotUseCase {

    private final TemplateRepository templates;

    public RedefineSlotService(TemplateRepository templates) {
        this.templates = templates;
    }

    @Transactional
    @Override
    public void handle(Input input) {
        TemplateId id = input.id();
        Slot slot = input.slot();
        MemeTemplate template = templates.getForUpdate(id);
        template.redefineSlot(slot);
        templates.save(template);
    }
}

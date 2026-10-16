package com.wtm.curation.usecase.service;

import com.wtm.curation.entity.MemeTemplate;
import com.wtm.curation.usecase.port.in.command.RestoreProfileUseCase;
import com.wtm.curation.usecase.port.out.template.TemplateRepository;
import com.wtm.shared.TemplateId;
import java.time.Clock;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class RestoreProfileService implements RestoreProfileUseCase {

    private final TemplateRepository templates;
    private final Clock clock;

    public RestoreProfileService(TemplateRepository templates, Clock clock) {
        this.templates = templates;
        this.clock = clock;
    }

    @Transactional
    @Override
    public boolean handle(UUID templateId) {
        MemeTemplate template = templates.findByIdForUpdate(new TemplateId(templateId)).orElse(null);
        if (template == null || !template.restoreProfile(clock.instant())) {
            return false;
        }
        templates.save(template);
        return true;
    }

    @Override
    public boolean canRestore(UUID templateId) {
        return templates.findById(new TemplateId(templateId)).map(MemeTemplate::canRestoreProfile).orElse(false);
    }
}

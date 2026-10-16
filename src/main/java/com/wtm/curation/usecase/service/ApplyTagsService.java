package com.wtm.curation.usecase.service;

import com.wtm.curation.entity.MemeTemplate;
import com.wtm.curation.entity.TemplateStatus;
import com.wtm.curation.usecase.port.in.command.ApplyTagsUseCase;
import com.wtm.curation.usecase.port.out.template.TemplateRepository;
import com.wtm.shared.MemeProfile;
import com.wtm.shared.TemplateId;
import com.wtm.shared.port.ImageTags;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes what the vision model saw into the library entry and publishes it, or withdraws the entry
 * when the picture turned out not to be a meme. Kept apart from the (slow) vision call so the
 * database row is locked only for the moment it takes to write.
 */
public class ApplyTagsService implements ApplyTagsUseCase {

    private final TemplateRepository templates;

    public ApplyTagsService(TemplateRepository templates) {
        this.templates = templates;
    }

    @Transactional
    @Override
    public void handle(UUID templateId, ImageTags tags) {
        MemeTemplate template = templates.getForUpdate(new TemplateId(templateId));
        if (template.status() != TemplateStatus.DRAFT) {
            return;   // someone published, edited or withdrew it meanwhile; leave their decision alone
        }
        if (!tags.isMeme()) {
            // Kept (so the same picture is not collected again) but never shown or searched.
            template.retire();
            templates.save(template);
            return;
        }

        List<String> aliases = List.of();
        if (!tags.title().isBlank()) {
            if (IngestMemeService.PLACEHOLDER_NAME.equals(template.name())) {
                template.rename(tags.title());
            } else if (!tags.title().equalsIgnoreCase(template.name())) {
                aliases = List.of(tags.title());
            }
        }
        MemeProfile profile = new MemeProfile(tags.meaning(), tags.usageExamples(), tags.emotions(),
                aliases, tags.imageText(), tags.tags());
        if (!profile.isComplete()) {
            throw new IllegalStateException("The description is missing the meaning or a usage example");
        }
        template.reviseProfile(profile);
        template.approve();
        templates.save(template);
    }
}

package com.usethatmeme.application.collection;

import com.usethatmeme.application.port.out.TemplateRepository;
import com.usethatmeme.domain.template.MemeProfile;
import com.usethatmeme.domain.template.MemeTemplate;
import com.usethatmeme.domain.template.TemplateId;
import com.usethatmeme.domain.template.TemplateStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes what the vision model saw into the library entry and publishes it, or withdraws the entry
 * when the picture turned out not to be a meme. Kept apart from the (slow) vision call so the
 * database row is locked only for the moment it takes to write.
 */
public class ApplyTagsHandler {

    private final TemplateRepository templates;

    public ApplyTagsHandler(TemplateRepository templates) {
        this.templates = templates;
    }

    @Transactional
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
            if (IngestMemeHandler.PLACEHOLDER_NAME.equals(template.name())) {
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

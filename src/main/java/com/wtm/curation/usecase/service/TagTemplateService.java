package com.wtm.curation.usecase.service;

import com.wtm.curation.usecase.port.in.command.TagTemplateUseCase;
import com.wtm.curation.usecase.port.in.query.TemplateView;
import com.wtm.curation.usecase.port.out.library.LibraryPort;
import com.wtm.curation.usecase.port.out.tagging.TaggingQueuePort;
import com.wtm.curation.usecase.port.out.template.TemplateReadPort;
import com.wtm.shared.ImageTypes;
import com.wtm.shared.Reference;
import com.wtm.shared.port.ImageTags;
import com.wtm.shared.port.ObjectStoragePort;
import com.wtm.shared.port.VisionTaggerPort;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Has the vision model look at one waiting picture and publishes what it found.
 * A failure puts the picture back in line, up to a limit.
 */
public class TagTemplateService implements TagTemplateUseCase {

    private static final Logger log = LoggerFactory.getLogger(TagTemplateService.class);

    private final TemplateReadPort templates;
    private final LibraryPort library;
    private final ObjectStoragePort storage;
    private final VisionTaggerPort tagger;
    private final ApplyTagsService apply;
    private final TaggingQueuePort queue;
    private final int maxAttempts;

    public TagTemplateService(TemplateReadPort templates, LibraryPort library, ObjectStoragePort storage,
                              VisionTaggerPort tagger, ApplyTagsService apply, TaggingQueuePort queue, int maxAttempts) {
        this.templates = templates;
        this.library = library;
        this.storage = storage;
        this.tagger = tagger;
        this.apply = apply;
        this.queue = queue;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public void handle(UUID templateId) {
        try {
            TemplateView template = templates.findById(templateId).orElse(null);
            if (template == null || !"DRAFT".equals(template.status())) {
                queue.done(templateId);   // gone, or already dealt with by hand
                return;
            }
            byte[] image = storage.get(template.imageKey());
            String hint = IngestMemeService.PLACEHOLDER_NAME.equals(template.name()) ? null : template.name();
            Reference reference = library.findReference(templateId).orElse(null);
            ImageTags tags = tagger.describe(image, ImageTypes.contentTypeOf(template.imageKey()), hint, reference);
            apply.handle(templateId, tags);
            queue.done(templateId);
        } catch (RuntimeException e) {
            log.warn("Tagging {} failed: {}", templateId, e.getMessage());
            queue.fail(templateId, truncate(e.getMessage()), maxAttempts);
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}

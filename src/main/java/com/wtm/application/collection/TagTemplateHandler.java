package com.wtm.application.collection;

import com.wtm.application.port.out.ObjectStoragePort;
import com.wtm.application.port.out.TaggingQueuePort;
import com.wtm.application.port.out.TemplateReadPort;
import com.wtm.application.port.out.VisionTaggerPort;
import com.wtm.application.template.query.TemplateView;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Has the vision model look at one waiting picture and publishes what it found.
 * A failure puts the picture back in line, up to a limit.
 */
public class TagTemplateHandler {

    private static final Logger log = LoggerFactory.getLogger(TagTemplateHandler.class);

    private final TemplateReadPort templates;
    private final ObjectStoragePort storage;
    private final VisionTaggerPort tagger;
    private final ApplyTagsHandler apply;
    private final TaggingQueuePort queue;
    private final int maxAttempts;

    public TagTemplateHandler(TemplateReadPort templates, ObjectStoragePort storage, VisionTaggerPort tagger,
                              ApplyTagsHandler apply, TaggingQueuePort queue, int maxAttempts) {
        this.templates = templates;
        this.storage = storage;
        this.tagger = tagger;
        this.apply = apply;
        this.queue = queue;
        this.maxAttempts = maxAttempts;
    }

    public void handle(UUID templateId) {
        try {
            TemplateView template = templates.findById(templateId).orElse(null);
            if (template == null || !"DRAFT".equals(template.status())) {
                queue.done(templateId);   // gone, or already dealt with by hand
                return;
            }
            byte[] image = storage.get(template.imageKey());
            String hint = IngestMemeHandler.PLACEHOLDER_NAME.equals(template.name()) ? null : template.name();
            ImageTags tags = tagger.describe(image, contentTypeOf(template.imageKey()), hint);
            apply.handle(templateId, tags);
            queue.done(templateId);
        } catch (RuntimeException e) {
            log.warn("Tagging {} failed: {}", templateId, e.getMessage());
            queue.fail(templateId, truncate(e.getMessage()), maxAttempts);
        }
    }

    public static String contentTypeOf(String imageKey) {
        String key = imageKey.toLowerCase();
        if (key.endsWith(".png")) {
            return "image/png";
        }
        if (key.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }

    private static String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}

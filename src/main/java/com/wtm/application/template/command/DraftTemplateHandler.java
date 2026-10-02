package com.wtm.application.template.command;

import com.wtm.application.port.out.ImageInspectorPort;
import com.wtm.application.port.out.ImageInspectorPort.ImageInfo;
import com.wtm.application.port.out.ObjectStoragePort;
import com.wtm.application.port.out.TemplateRepository;
import com.wtm.domain.template.MemeTemplate;
import com.wtm.domain.template.TemplateId;

public class DraftTemplateHandler {

    private final ImageInspectorPort inspector;
    private final ObjectStoragePort storage;
    private final TemplateRepository templates;

    public DraftTemplateHandler(ImageInspectorPort inspector, ObjectStoragePort storage,
                                TemplateRepository templates) {
        this.inspector = inspector;
        this.storage = storage;
        this.templates = templates;
    }

    public TemplateId handle(String name, byte[] image) {
        ImageInfo info = inspector.inspect(image);
        TemplateId id = TemplateId.newId();
        String key = "templates/" + id.value() + "." + info.extension();
        MemeTemplate template = MemeTemplate.draft(id, name, key, info.width(), info.height());

        storage.put(key, image, info.contentType());
        try {
            templates.save(template);
        } catch (RuntimeException e) {
            deleteQuietly(key, e);
            throw e;
        }
        return id;
    }

    private void deleteQuietly(String key, RuntimeException cause) {
        try {
            storage.delete(key);
        } catch (RuntimeException cleanupFailure) {
            cause.addSuppressed(cleanupFailure);
        }
    }
}

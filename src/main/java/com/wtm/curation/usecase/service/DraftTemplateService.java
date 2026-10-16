package com.wtm.curation.usecase.service;

import com.wtm.curation.entity.MemeTemplate;
import com.wtm.curation.usecase.port.in.command.DraftTemplateUseCase;
import com.wtm.curation.usecase.port.out.image.ImageInspectorPort;
import com.wtm.curation.usecase.port.out.image.ImageInspectorPort.InspectedImage;
import com.wtm.curation.usecase.port.out.template.TemplateRepository;
import com.wtm.shared.TemplateId;
import com.wtm.shared.port.ObjectStoragePort;

public class DraftTemplateService implements DraftTemplateUseCase {

    private final ImageInspectorPort inspector;
    private final ObjectStoragePort storage;
    private final TemplateRepository templates;

    public DraftTemplateService(ImageInspectorPort inspector, ObjectStoragePort storage,
                                TemplateRepository templates) {
        this.inspector = inspector;
        this.storage = storage;
        this.templates = templates;
    }

    @Override
    public TemplateId handle(Input input) {
        String name = input.name();
        byte[] image = input.image();
        InspectedImage info = inspector.inspect(image);
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

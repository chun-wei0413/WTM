package com.memehub.application.template.query;

import com.memehub.application.TemplateNotFoundException;
import com.memehub.application.port.out.ObjectStoragePort;
import com.memehub.application.port.out.TemplateReadPort;
import com.memehub.domain.template.TemplateId;
import java.time.Duration;
import java.util.UUID;

public class GetTemplateHandler {

    private static final Duration IMAGE_URL_TTL = Duration.ofMinutes(15);

    private final TemplateReadPort reads;
    private final ObjectStoragePort storage;

    public GetTemplateHandler(TemplateReadPort reads, ObjectStoragePort storage) {
        this.reads = reads;
        this.storage = storage;
    }

    public TemplateView handle(UUID id) {
        TemplateView view = reads.findById(id)
                .orElseThrow(() -> new TemplateNotFoundException(new TemplateId(id)));
        return view.withImageUrl(storage.presignedGetUrl(view.imageKey(), IMAGE_URL_TTL));
    }
}

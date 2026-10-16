package com.wtm.curation.usecase.service;

import com.wtm.curation.usecase.port.in.query.GetTemplateUseCase;
import com.wtm.curation.usecase.port.in.query.TemplateView;
import com.wtm.curation.usecase.port.out.template.TemplateReadPort;
import com.wtm.shared.TemplateId;
import com.wtm.shared.TemplateNotFoundException;
import com.wtm.shared.port.ObjectStoragePort;
import java.time.Duration;
import java.util.UUID;

public class GetTemplateService implements GetTemplateUseCase {

    private static final Duration IMAGE_URL_TTL = Duration.ofMinutes(15);

    private final TemplateReadPort reads;
    private final ObjectStoragePort storage;

    public GetTemplateService(TemplateReadPort reads, ObjectStoragePort storage) {
        this.reads = reads;
        this.storage = storage;
    }

    @Override
    public TemplateView handle(UUID id) {
        TemplateView view = reads.findById(id)
                .orElseThrow(() -> new TemplateNotFoundException(new TemplateId(id)));
        return view.withImageUrl(storage.presignedGetUrl(view.imageKey(), IMAGE_URL_TTL));
    }
}

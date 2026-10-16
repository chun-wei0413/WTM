package com.wtm.curation.usecase.service;

import com.wtm.curation.usecase.port.in.query.ListTemplatesUseCase;
import com.wtm.curation.usecase.port.in.query.TemplateSummary;
import com.wtm.curation.usecase.port.out.template.TemplateReadPort;
import com.wtm.shared.port.ObjectStoragePort;
import java.time.Duration;
import java.util.List;

public class ListTemplatesService implements ListTemplatesUseCase {

    private static final Duration IMAGE_URL_TTL = Duration.ofMinutes(15);

    private final TemplateReadPort reads;
    private final ObjectStoragePort storage;

    public ListTemplatesService(TemplateReadPort reads, ObjectStoragePort storage) {
        this.reads = reads;
        this.storage = storage;
    }

    @Override
    public List<TemplateSummary> handle(String status) {
        return reads.list(status).stream()
                .map(s -> s.withImageUrl(storage.presignedGetUrl(s.imageKey(), IMAGE_URL_TTL)))
                .toList();
    }
}

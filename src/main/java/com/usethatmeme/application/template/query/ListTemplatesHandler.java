package com.usethatmeme.application.template.query;

import com.usethatmeme.application.port.out.ObjectStoragePort;
import com.usethatmeme.application.port.out.TemplateReadPort;
import java.time.Duration;
import java.util.List;

public class ListTemplatesHandler {

    private static final Duration IMAGE_URL_TTL = Duration.ofMinutes(15);

    private final TemplateReadPort reads;
    private final ObjectStoragePort storage;

    public ListTemplatesHandler(TemplateReadPort reads, ObjectStoragePort storage) {
        this.reads = reads;
        this.storage = storage;
    }

    public List<TemplateSummary> handle(String status) {
        return reads.list(status).stream()
                .map(s -> s.withImageUrl(storage.presignedGetUrl(s.imageKey(), IMAGE_URL_TTL)))
                .toList();
    }
}

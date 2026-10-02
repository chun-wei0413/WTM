package com.usethatmeme.application.template.query;

import java.time.Instant;
import java.util.UUID;

public record TemplateSummary(UUID id, String name, String status, int version,
                              String imageKey, String imageUrl, Instant updatedAt) {

    public TemplateSummary withImageUrl(String url) {
        return new TemplateSummary(id, name, status, version, imageKey, url, updatedAt);
    }
}

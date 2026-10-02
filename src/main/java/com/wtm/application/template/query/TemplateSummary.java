package com.wtm.application.template.query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One line of the administrator's list of library entries: enough to tell what a picture is without opening it.
 */
public record TemplateSummary(UUID id, String name, String status, int version,
                              String imageKey, String imageUrl, Instant updatedAt,
                              String meaning, List<String> tags, String sourceType, String attribution) {

    public TemplateSummary withImageUrl(String url) {
        return new TemplateSummary(id, name, status, version, imageKey, url, updatedAt, meaning, tags, sourceType,
                attribution);
    }
}

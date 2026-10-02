package com.usethatmeme.application.template.query;

import com.usethatmeme.domain.template.MemeProfile;
import com.usethatmeme.domain.template.Slot;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TemplateView(UUID id, String name, String status, int version,
                           int imageWidth, int imageHeight, String imageKey, String imageUrl,
                           MemeProfile profile, List<Slot> slots,
                           Instant createdAt, Instant updatedAt) {

    public TemplateView withImageUrl(String url) {
        return new TemplateView(id, name, status, version, imageWidth, imageHeight, imageKey, url,
                profile, slots, createdAt, updatedAt);
    }
}

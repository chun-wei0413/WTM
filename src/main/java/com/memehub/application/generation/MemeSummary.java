package com.memehub.application.generation;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MemeSummary(UUID id, UUID templateId, String templateName, String status,
                          String imageUrl, Map<Integer, String> captions, Instant createdAt) {
}

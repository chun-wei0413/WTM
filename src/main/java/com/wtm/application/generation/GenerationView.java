package com.wtm.application.generation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record GenerationView(UUID id, String status, String situation, String failureReason,
                             Instant createdAt, List<Candidate> candidates) {

    public record Candidate(UUID memeId, UUID templateId, String templateName, String status,
                            String imageUrl, Map<Integer, String> captions) {
    }
}

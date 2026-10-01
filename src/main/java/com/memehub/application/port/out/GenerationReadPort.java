package com.memehub.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read side of generation jobs and the memes they produced.
 */
public interface GenerationReadPort {

    Optional<JobSnapshot> find(UUID jobId);

    record JobSnapshot(UUID id, UUID requesterId, String situation, String status, String failureReason,
                       Instant createdAt, List<CandidateRow> candidates) {
    }

    record CandidateRow(UUID memeId, UUID templateId, String templateName, String memeStatus,
                        String imageKey, Map<Integer, String> captions) {
    }
}

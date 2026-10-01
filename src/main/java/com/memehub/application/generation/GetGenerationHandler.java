package com.memehub.application.generation;

import com.memehub.application.port.out.GenerationReadPort;
import com.memehub.application.port.out.GenerationReadPort.JobSnapshot;
import com.memehub.application.port.out.ObjectStoragePort;
import java.time.Duration;
import java.util.UUID;

public class GetGenerationHandler {

    private static final Duration IMAGE_URL_TTL = Duration.ofMinutes(15);

    private final GenerationReadPort reads;
    private final ObjectStoragePort storage;

    public GetGenerationHandler(GenerationReadPort reads, ObjectStoragePort storage) {
        this.reads = reads;
        this.storage = storage;
    }

    /** A job is only visible to the user who asked for it. */
    public GenerationView handle(UUID requesterId, UUID jobId) {
        JobSnapshot job = reads.find(jobId)
                .filter(j -> j.requesterId().equals(requesterId))
                .orElseThrow(() -> new GenerationNotFoundException(jobId));
        var candidates = job.candidates().stream()
                .map(c -> new GenerationView.Candidate(c.memeId(), c.templateId(), c.templateName(),
                        c.memeStatus(), c.imageKey() == null ? null : storage.presignedGetUrl(c.imageKey(), IMAGE_URL_TTL),
                        c.captions()))
                .toList();
        return new GenerationView(job.id(), job.status(), job.situation(), job.failureReason(),
                job.createdAt(), candidates);
    }
}

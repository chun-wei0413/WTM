package com.wtm.curation.usecase.service;

import com.wtm.curation.usecase.port.in.command.TaggingQueueUseCase;
import com.wtm.curation.usecase.port.out.tagging.TaggingQueuePort;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * What a tagging worker needs from the line: take work, and rescue work that was abandoned.
 */
public class TaggingQueueService implements TaggingQueueUseCase {

    private final TaggingQueuePort queue;

    public TaggingQueueService(TaggingQueuePort queue) {
        this.queue = queue;
    }

    @Override
    public List<UUID> claim(int limit) {
        return queue.claim(limit);
    }

    @Override
    public int recoverStale(Duration beingTaggedLongerThan, int maxAttempts) {
        return queue.recoverStale(beingTaggedLongerThan, maxAttempts);
    }
}

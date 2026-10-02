package com.usethatmeme.application.collection;

import com.usethatmeme.application.port.out.TaggingQueuePort;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * What a tagging worker needs from the line: take work, and rescue work that was abandoned.
 */
public class TaggingQueueHandler {

    private final TaggingQueuePort queue;

    public TaggingQueueHandler(TaggingQueuePort queue) {
        this.queue = queue;
    }

    public List<UUID> claim(int limit) {
        return queue.claim(limit);
    }

    public int recoverStale(Duration beingTaggedLongerThan, int maxAttempts) {
        return queue.recoverStale(beingTaggedLongerThan, maxAttempts);
    }
}

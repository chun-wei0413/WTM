package com.memehub.application.port.out;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * The line of pictures waiting to be looked at. Claiming is safe across threads and instances.
 * (Entries join the line when they are added to the library, see {@link LibraryPort}.)
 */
public interface TaggingQueuePort {

    /** Atomically moves up to {@code limit} waiting entries to "being tagged" and returns them. */
    List<UUID> claim(int limit);

    void done(UUID templateId);

    /** Puts the entry back in line, or marks it failed once it has used up {@code maxAttempts}. */
    void fail(UUID templateId, String error, int maxAttempts);

    /**
     * Gives entries that have been "being tagged" for too long (a crashed worker) another chance.
     *
     * @return how many entries were touched
     */
    int recoverStale(Duration beingTaggedLongerThan, int maxAttempts);
}

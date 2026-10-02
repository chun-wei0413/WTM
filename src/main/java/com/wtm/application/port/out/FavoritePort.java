package com.wtm.application.port.out;

import com.wtm.application.port.out.LibraryBrowsePort.LibraryCard;
import java.util.List;
import java.util.UUID;

/**
 * Each user's own shortlist of library memes.
 */
public interface FavoritePort {

    /**
     * Adds the entry to the user's favorites. Adding it twice changes nothing.
     *
     * @return false when there is no published entry with that id
     */
    boolean add(UUID userId, UUID templateId);

    /** Removes the entry; removing one that is not a favorite changes nothing. */
    void remove(UUID userId, UUID templateId);

    /** The user's favorites that are still published, most recently added first. */
    List<LibraryCard> list(UUID userId);
}

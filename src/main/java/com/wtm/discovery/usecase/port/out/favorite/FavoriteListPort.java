package com.wtm.discovery.usecase.port.out.favorite;

import com.wtm.discovery.usecase.port.out.library.LibraryBrowsePort.LibraryCard;
import java.util.List;
import java.util.UUID;

/**
 * What the user's shortlist shows: the favorites that are still published, as cards. Reads only.
 */
public interface FavoriteListPort {

    /** The user's favorites that are still published, most recently added first. */
    List<LibraryCard> list(UUID userId);
}

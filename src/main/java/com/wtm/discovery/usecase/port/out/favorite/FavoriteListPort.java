package com.wtm.discovery.usecase.port.out.favorite;

import com.wtm.discovery.usecase.port.out.library.LibraryBrowsePort.LibraryCard;
import com.wtm.shared.UserId;
import java.util.List;

/**
 * What the user's shortlist shows: the favorites that are still published, as cards. Reads only.
 */
public interface FavoriteListPort {

    /** The user's favorites that are still published, most recently added first. */
    List<LibraryCard> list(UserId userId);
}

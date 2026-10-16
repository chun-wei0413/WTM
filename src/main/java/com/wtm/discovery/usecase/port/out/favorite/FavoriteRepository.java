package com.wtm.discovery.usecase.port.out.favorite;

import com.wtm.discovery.entity.Favorite;
import com.wtm.shared.UserId;
import java.util.Optional;
import java.util.UUID;

/**
 * Where users' favorites are kept.
 */
public interface FavoriteRepository {

    Optional<Favorite> find(UserId userId, UUID templateId);

    /** Keeps the favorite. When the user already has it, nothing changes (it keeps the time it was first made). */
    void save(Favorite favorite);

    /** Removes the favorite; removing one that is not there changes nothing. */
    void remove(UserId userId, UUID templateId);
}

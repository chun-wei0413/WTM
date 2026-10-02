package com.memehub.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read side of memes, for showing a user their own.
 */
public interface MemeReadPort {

    /** The owner's memes with the given status that have an image, newest first. */
    List<MemeRow> listByOwner(UUID ownerId, String status, int limit);

    record MemeRow(UUID id, UUID templateId, String templateName, String status, String imageKey,
                   Map<Integer, String> captions, Instant createdAt) {
    }
}

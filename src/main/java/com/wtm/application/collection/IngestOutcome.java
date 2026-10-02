package com.wtm.application.collection;

import java.util.UUID;

/**
 * What happened to one picture offered to the library.
 *
 * @param templateId the new entry, or the existing one when the picture was already collected
 */
public record IngestOutcome(Status status, UUID templateId, String reason) {

    public enum Status {
        /** Added; it will be looked at by the vision model before it can be found. */
        IMPORTED,
        /** The same picture, or a near copy of one, is already in the library. */
        DUPLICATE,
        /** Not a usable picture (unreadable, wrong format, too small). */
        REJECTED
    }

    public static IngestOutcome imported(UUID id) {
        return new IngestOutcome(Status.IMPORTED, id, null);
    }

    public static IngestOutcome duplicate(UUID existing) {
        return new IngestOutcome(Status.DUPLICATE, existing, "Already in the library");
    }

    public static IngestOutcome rejected(String reason) {
        return new IngestOutcome(Status.REJECTED, null, reason);
    }
}

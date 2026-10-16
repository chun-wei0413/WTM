package com.wtm.curation.entity;

import com.wtm.shared.MemeProfile;
import java.time.Instant;
import java.util.UUID;

/**
 * One time the description of a template was replaced by a proposal, kept so that it can be taken back.
 */
public final class ProfileChange {

    /** Who decided the change. */
    public enum Source {
        ADMIN,
        AUTO
    }

    private final UUID id;
    private final MemeProfile before;
    private final MemeProfile after;
    private final Source source;
    private final Instant createdAt;
    private final Instant undoneAt;

    public ProfileChange(UUID id, MemeProfile before, MemeProfile after, Source source, Instant createdAt,
                         Instant undoneAt) {
        this.id = id;
        this.before = before;
        this.after = after;
        this.source = source;
        this.createdAt = createdAt;
        this.undoneAt = undoneAt;
    }

    public UUID id() {
        return id;
    }

    public MemeProfile before() {
        return before;
    }

    public MemeProfile after() {
        return after;
    }

    public Source source() {
        return source;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** Null while the change stands. */
    public Instant undoneAt() {
        return undoneAt;
    }

    public boolean isUndone() {
        return undoneAt != null;
    }

    ProfileChange undone(Instant now) {
        return new ProfileChange(id, before, after, source, createdAt, now);
    }
}

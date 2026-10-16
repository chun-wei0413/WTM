package com.wtm.discovery.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A meme a user wants to reach again without searching. A user has at most one favorite per meme, and it is
 * identified by the two together.
 *
 * <p>Whether the meme may be favorited (it must be published) is Curation's to say, so it is asked before a
 * favorite is made, not checked here.
 */
public final class Favorite {

    private final UUID userId;
    private final UUID templateId;
    private final Instant createdAt;

    private Favorite(UUID userId, UUID templateId, Instant createdAt) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.templateId = Objects.requireNonNull(templateId, "templateId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** The user makes the meme a favorite. */
    public static Favorite make(UUID userId, UUID templateId, Instant now) {
        return new Favorite(userId, templateId, now);
    }

    /** A favorite as it was kept. */
    public static Favorite restore(UUID userId, UUID templateId, Instant createdAt) {
        return new Favorite(userId, templateId, createdAt);
    }

    public UUID userId() {
        return userId;
    }

    public UUID templateId() {
        return templateId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Favorite f && userId.equals(f.userId) && templateId.equals(f.templateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, templateId);
    }
}

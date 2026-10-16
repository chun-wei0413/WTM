package com.wtm.feedback.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * What one person said about one meme. It stays open until the reports about the meme are closed together.
 */
public final class Report {

    /**
     * How a report was closed.
     *
     * @param automatic true when the rules closed it, false when an administrator did
     * @param note why, in the vision model's words when it was the model's proposal that decided
     */
    public record Closing(Resolution resolution, boolean automatic, String note, Instant at) {
    }

    private final UUID id;
    private final UUID userId;
    private final ReportReason reason;
    private final String comment;
    private final Instant createdAt;
    private final Closing closing;

    public Report(UUID id, UUID userId, ReportReason reason, String comment, Instant createdAt, Closing closing) {
        this.id = id;
        this.userId = userId;
        this.reason = reason;
        this.comment = comment;
        this.createdAt = createdAt;
        this.closing = closing;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public ReportReason reason() {
        return reason;
    }

    public String comment() {
        return comment;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** Null while the report is open. */
    public Closing closing() {
        return closing;
    }

    public boolean isOpen() {
        return closing == null;
    }

    Report closedWith(Closing by) {
        return new Report(id, userId, reason, comment, createdAt, by);
    }

    Report reopened() {
        return new Report(id, userId, reason, comment, createdAt, null);
    }
}

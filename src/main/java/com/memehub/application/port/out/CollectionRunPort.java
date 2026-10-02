package com.memehub.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Records of "collect from this source" requests and how they went.
 */
public interface CollectionRunPort {

    void create(UUID id, String source, String options);

    boolean anyRunning();

    void update(UUID id, Counts counts);

    void finish(UUID id, boolean succeeded, String message);

    /** Marks runs that were still going when the application stopped as failed. */
    int failInterrupted();

    List<RunView> recent(int limit);

    Optional<RunView> find(UUID id);

    record Counts(int found, int imported, int duplicates, int rejected, int failed) {

        public static Counts none() {
            return new Counts(0, 0, 0, 0, 0);
        }
    }

    record RunView(UUID id, String source, String options, String status, Counts counts, String message,
                   Instant startedAt, Instant finishedAt) {
    }
}

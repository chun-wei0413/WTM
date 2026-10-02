package com.wtm.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Remembers what people searched for, so the most common phrases can be offered as shortcuts.
 */
public interface SearchLogPort {

    void record(UUID userId, String term);

    /** The phrases searched most since the given time, most searched first. */
    List<HotTerm> hot(Instant since, int limit);

    record HotTerm(String term, int searches) {
    }
}

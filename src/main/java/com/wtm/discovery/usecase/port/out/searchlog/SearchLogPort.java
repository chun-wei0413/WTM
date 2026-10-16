package com.wtm.discovery.usecase.port.out.searchlog;

import com.wtm.discovery.usecase.port.in.query.HotTerm;
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

}

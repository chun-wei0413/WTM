package com.memehub.application.collection;

import com.memehub.application.port.out.CollectionRunPort;
import com.memehub.application.port.out.MemeSourcePort;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Checks a request to collect from a source and records it as a run. The work itself is done
 * afterwards by {@link RunCollectionHandler}, off the request thread.
 */
public class StartCollectionHandler {

    public static final int MAX_LIMIT = 300;

    private final List<MemeSourcePort> sources;
    private final CollectionRunPort runs;

    public StartCollectionHandler(List<MemeSourcePort> sources, CollectionRunPort runs) {
        this.sources = sources;
        this.runs = runs;
    }

    public record Plan(UUID runId, MemeSourcePort source, int limit, Map<String, String> options) {
    }

    /**
     * @param limit how many pictures to look at, 1 to {@value #MAX_LIMIT}
     * @throws IllegalArgumentException for an unknown source or a limit out of range
     * @throws CollectionBusyException when another collection is still running
     */
    public Plan handle(String sourceId, int limit, Map<String, String> options) {
        MemeSourcePort source = sources.stream()
                .filter(s -> s.id().equalsIgnoreCase(sourceId == null ? "" : sourceId.strip()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown source: " + sourceId));
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        if (runs.anyRunning()) {
            throw new CollectionBusyException();
        }
        Map<String, String> cleaned = new TreeMap<>(options == null ? Map.of() : options);
        UUID id = UUID.randomUUID();
        runs.create(id, source.id(), cleaned.toString());
        return new Plan(id, source, limit, cleaned);
    }
}

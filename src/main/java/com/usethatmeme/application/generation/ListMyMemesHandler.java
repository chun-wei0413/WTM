package com.usethatmeme.application.generation;

import com.usethatmeme.application.port.out.MemeReadPort;
import com.usethatmeme.application.port.out.ObjectStoragePort;
import com.usethatmeme.domain.meme.MemeStatus;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public class ListMyMemesHandler {

    public static final int MAX_LIMIT = 100;
    private static final Duration IMAGE_URL_TTL = Duration.ofMinutes(15);

    private final MemeReadPort reads;
    private final ObjectStoragePort storage;

    public ListMyMemesHandler(MemeReadPort reads, ObjectStoragePort storage) {
        this.reads = reads;
        this.storage = storage;
    }

    /**
     * @param status {@code KEPT} or {@code COMPOSED}; defaults to the memes the user chose to keep
     * @throws IllegalArgumentException for an unknown status or a limit outside 1..{@value #MAX_LIMIT}
     */
    public List<MemeSummary> handle(UUID ownerId, String status, int limit) {
        MemeStatus wanted = status == null || status.isBlank()
                ? MemeStatus.KEPT
                : MemeStatus.valueOf(status.strip().toUpperCase());
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        return reads.listByOwner(ownerId, wanted.name(), limit).stream()
                .map(row -> new MemeSummary(row.id(), row.templateId(), row.templateName(), row.status(),
                        storage.presignedGetUrl(row.imageKey(), IMAGE_URL_TTL), row.captions(), row.createdAt()))
                .toList();
    }
}

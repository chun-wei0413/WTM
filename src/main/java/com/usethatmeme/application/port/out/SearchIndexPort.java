package com.usethatmeme.application.port.out;

import com.usethatmeme.domain.template.MemeProfile;
import com.usethatmeme.domain.template.Slot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Write side of the template search read model. The index is derived data: it is
 * brought in line with the templates by comparing state, never by replaying changes.
 */
public interface SearchIndexPort {

    /** Approved templates that have no index entry, or whose entry is out of date. */
    List<UUID> findTemplatesNeedingIndex(int limit);

    /** Index entries whose template is no longer approved (or no longer exists). */
    List<UUID> findIndexEntriesToRemove(int limit);

    Optional<IndexableTemplate> load(UUID templateId);

    void upsert(UUID templateId, String searchText, float[] embedding,
                List<Slot> slotLayout, Instant sourceUpdatedAt);

    void remove(UUID templateId);

    record IndexableTemplate(UUID id, String name, String status, MemeProfile profile,
                             List<Slot> slots, Instant updatedAt) {
    }
}

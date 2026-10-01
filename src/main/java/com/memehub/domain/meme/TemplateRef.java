package com.memehub.domain.meme;

import com.memehub.domain.template.Slot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The template a meme was made from: its id and version plus a snapshot of the
 * slots at that time. A meme keeps working even after the template is revised or retired.
 */
public record TemplateRef(UUID templateId, int version, List<Slot> slots) {

    public TemplateRef {
        Objects.requireNonNull(templateId, "templateId");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        slots = List.copyOf(slots);
    }

    public Optional<Slot> slot(int slotNo) {
        return slots.stream().filter(s -> s.slotNo() == slotNo).findFirst();
    }
}

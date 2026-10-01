package com.memehub.application.template.search;

import com.memehub.domain.template.Slot;
import java.util.List;
import java.util.UUID;

public record SearchResult(UUID templateId, String name, double score, String imageUrl, List<Slot> slots) {
}

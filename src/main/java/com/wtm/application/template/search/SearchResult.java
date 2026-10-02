package com.wtm.application.template.search;

import com.wtm.domain.template.Slot;
import java.util.List;
import java.util.UUID;

public record SearchResult(UUID templateId, String name, double score, String imageUrl, List<Slot> slots,
                           String meaning, List<String> tags, String imageText, String sourceType,
                           String sourceUrl, String attribution) {
}

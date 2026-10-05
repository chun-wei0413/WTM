package com.wtm.application.template.search;

import com.wtm.domain.template.Slot;
import java.util.List;
import java.util.UUID;

public record SearchResult(UUID templateId, String name, double score, String imageUrl, int imageWidth, int imageHeight,
                           List<Slot> slots,
                           String meaning, List<String> usageExamples, List<String> emotions, List<String> tags,
                           String imageText, String sourceType, String sourceUrl, String attribution) {
}

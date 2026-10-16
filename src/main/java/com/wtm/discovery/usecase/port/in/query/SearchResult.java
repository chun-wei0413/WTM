package com.wtm.discovery.usecase.port.in.query;

import com.wtm.shared.Reference;
import com.wtm.shared.Slot;
import java.util.List;
import java.util.UUID;

public record SearchResult(UUID templateId, String name, double score, String imageUrl, int imageWidth, int imageHeight,
                           List<Slot> slots,
                           String meaning, List<String> usageExamples, List<String> emotions, List<String> tags,
                           String imageText, String sourceType, String sourceUrl, String attribution, Reference reference) {
}

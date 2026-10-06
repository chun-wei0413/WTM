package com.wtm.application.port.out;

import com.wtm.application.collection.Reference;
import com.wtm.domain.template.Slot;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Read side of template search. Each method returns template ids ranked best first.
 */
public interface TemplateSearchPort {

    List<UUID> byVector(float[] queryEmbedding, int limit);

    List<UUID> byKeyword(String query, int limit);

    List<SearchCard> cards(Collection<UUID> templateIds);

    /** Everything the library needs to show one hit. */
    record SearchCard(UUID id, String name, String imageKey, int imageWidth, int imageHeight, List<Slot> slots, String meaning,
                      List<String> usageExamples, List<String> emotions, List<String> tags, String imageText,
                      String sourceType, String sourceUrl, String attribution, Reference reference) {
    }
}

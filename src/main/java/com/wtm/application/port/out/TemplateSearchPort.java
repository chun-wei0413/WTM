package com.wtm.application.port.out;

import com.wtm.application.collection.Reference;
import com.wtm.domain.template.Slot;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Read side of template search.
 */
public interface TemplateSearchPort {

    /** The published memes whose meaning is nearest to the query, best first, with their cosine similarity. */
    List<Hit> byVector(float[] queryEmbedding, int limit);

    /** The searchable text of every published meme, which the application ranks by keywords itself (BM25). */
    List<IndexedText> searchTexts();

    /** Changes whenever {@link #searchTexts()} would answer differently, so a copy can be kept until then. */
    String searchTextsVersion();

    /** A meme and how well it matched, higher is better. */
    record Hit(UUID id, double score) {
    }

    record IndexedText(UUID id, String text) {
    }

    List<SearchCard> cards(Collection<UUID> templateIds);

    /** Everything the library needs to show one hit. */
    record SearchCard(UUID id, String name, String imageKey, int imageWidth, int imageHeight, List<Slot> slots, String meaning,
                      List<String> usageExamples, List<String> emotions, List<String> tags, String imageText,
                      String sourceType, String sourceUrl, String attribution, Reference reference) {
    }
}

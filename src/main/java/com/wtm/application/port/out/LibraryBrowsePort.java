package com.wtm.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Looking through the published part of the meme library without a search phrase.
 */
public interface LibraryBrowsePort {

    /** Published entries in random order. */
    List<LibraryCard> random(int limit);

    /** The stored picture of a published entry; empty for a draft, a retired entry or an unknown id. */
    Optional<LibraryImage> findPublishedImage(UUID templateId);

    record LibraryCard(UUID id, String name, String imageKey, String meaning, List<String> tags, String imageText,
                       String sourceType, String sourceUrl, String attribution) {
    }

    record LibraryImage(String imageKey, String name) {
    }
}

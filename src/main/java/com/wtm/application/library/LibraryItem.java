package com.wtm.application.library;

import com.wtm.application.collection.Reference;
import java.util.List;
import java.util.UUID;

/**
 * One meme of the library as the browsing pages show it.
 *
 * @param imageUrl a time-limited address the browser can show the picture from
 * @param reference an explanation of the meme written by its source, to be shown with its credit; null when there is none
 * @param imageWidth the picture's size, so a page can leave room for it before it has loaded
 */
public record LibraryItem(UUID templateId, String name, String imageUrl, int imageWidth, int imageHeight, String meaning, List<String> tags,
                          String imageText, String sourceType, String sourceUrl, String attribution, Reference reference) {
}

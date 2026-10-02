package com.wtm.application.library;

import java.util.List;
import java.util.UUID;

/**
 * One meme of the library as the browsing pages show it.
 *
 * @param imageUrl a time-limited address the browser can show the picture from
 */
public record LibraryItem(UUID templateId, String name, String imageUrl, String meaning, List<String> tags,
                          String imageText, String sourceType, String sourceUrl, String attribution) {
}

package com.wtm.curation.entity;

import com.wtm.shared.DomainRuleViolation;

/**
 * The picture of a template: where it is stored, how big it is, and, when it came through the library, the two
 * values that tell it apart from other pictures.
 *
 * @param sha256 identifies exactly these bytes; null for a picture added without them
 * @param perceptualHash what the picture looks like, so a resized copy has almost the same value; null likewise
 */
public record ImageInfo(String key, int width, int height, String sha256, Long perceptualHash) {

    public ImageInfo {
        if (key == null || key.isBlank()) {
            throw new DomainRuleViolation("Template image must be provided");
        }
        if (width <= 0 || height <= 0) {
            throw new DomainRuleViolation("Image size must be positive");
        }
    }

    /** A picture known only by where it is and how big it is. */
    public static ImageInfo of(String key, int width, int height) {
        return new ImageInfo(key, width, height, null, null);
    }
}

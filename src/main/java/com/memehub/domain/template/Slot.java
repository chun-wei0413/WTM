package com.memehub.domain.template;

/**
 * A text area on a meme template where a caption can be placed.
 * A template may have no slots at all (the image already carries its own text).
 */
public record Slot(int slotNo, String role, int maxChars, boolean required,
                   int x, int y, int width, int height) {

    public Slot {
        if (slotNo <= 0) {
            throw new IllegalArgumentException("slotNo must be positive");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
        if (maxChars <= 0) {
            throw new IllegalArgumentException("maxChars must be positive");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("slot area must have a positive size");
        }
    }

    public boolean fitsWithin(int imageWidth, int imageHeight) {
        return x >= 0 && y >= 0 && x + width <= imageWidth && y + height <= imageHeight;
    }

    public boolean accepts(String caption) {
        return caption != null && caption.codePointCount(0, caption.length()) <= maxChars;
    }
}

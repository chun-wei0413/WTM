package com.wtm.shared;

public final class ImageTypes {

    private ImageTypes() {
    }

    /** The content type of a stored picture, told from the ending of its key. */
    public static String contentTypeOf(String imageKey) {
        String key = imageKey.toLowerCase();
        if (key.endsWith(".png")) {
            return "image/png";
        }
        if (key.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }
}

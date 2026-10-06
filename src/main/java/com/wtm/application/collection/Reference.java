package com.wtm.application.collection;

/**
 * An explanation of a meme written by a source other than the vision model, with what is needed to credit it.
 *
 * @param text the explanation, as the source wrote it (an excerpt)
 * @param sourceName where it comes from, for example "Wikipedia (zh)"
 * @param url the page it was taken from
 * @param license the licence it is shared under, for example "CC BY-SA 4.0"
 */
public record Reference(String text, String sourceName, String url, String license) {

    /** The most text kept; longer explanations are cut, since it is only context for the model and a short credit line. */
    public static final int MAX_LENGTH = 1200;

    public Reference {
        text = text == null ? "" : text.strip();
        if (text.length() > MAX_LENGTH) {
            text = text.substring(0, MAX_LENGTH);
        }
    }

    /** @return the reference, or null when there is no text to keep */
    public static Reference ofNullable(String text, String sourceName, String url, String license) {
        return text == null || text.isBlank() ? null : new Reference(text, sourceName, url, license);
    }
}

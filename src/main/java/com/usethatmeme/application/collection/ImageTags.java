package com.usethatmeme.application.collection;

import java.util.List;

/**
 * What the vision model saw in a picture.
 *
 * @param isMeme whether it is a meme, reaction image or sticker at all (a plain photo,
 *               screenshot or advertisement is not)
 * @param title a short name for it
 * @param meaning what it expresses and why it is funny
 * @param usageExamples situations people would use it in, in everyday words
 * @param emotions the feelings it carries
 * @param tags keywords: what is shown, the topic, the joke format
 * @param imageText the words that appear in the picture, copied as they are
 */
public record ImageTags(boolean isMeme, String title, String meaning, List<String> usageExamples,
                        List<String> emotions, List<String> tags, String imageText) {

    public ImageTags {
        title = title == null ? "" : title.strip();
        meaning = meaning == null ? "" : meaning.strip();
        usageExamples = usageExamples == null ? List.of() : List.copyOf(usageExamples);
        emotions = emotions == null ? List.of() : List.copyOf(emotions);
        tags = tags == null ? List.of() : List.copyOf(tags);
        imageText = imageText == null ? "" : imageText.strip();
    }
}

package com.wtm.domain.template;

import java.util.List;

/**
 * What a meme means and when to use it: the knowledge that makes it findable by describing it,
 * independent of how many caption slots it has.
 *
 * @param meaning what the meme expresses
 * @param usageExamples situations people use it in, in everyday words
 * @param emotions the feelings it carries
 * @param aliases other names it is known by
 * @param imageText the words that appear in the image itself, if any
 * @param tags short keywords: what is shown, the topic, the joke format
 */
public record MemeProfile(String meaning, List<String> usageExamples, List<String> emotions,
                          List<String> aliases, String imageText, List<String> tags) {

    public MemeProfile {
        meaning = meaning == null ? "" : meaning.strip();
        usageExamples = usageExamples == null ? List.of() : List.copyOf(usageExamples);
        emotions = emotions == null ? List.of() : List.copyOf(emotions);
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        imageText = imageText == null ? "" : imageText.strip();
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /** A profile without image text or tags, as written by hand for a template. */
    public MemeProfile(String meaning, List<String> usageExamples, List<String> emotions, List<String> aliases) {
        this(meaning, usageExamples, emotions, aliases, "", List.of());
    }

    public static MemeProfile empty() {
        return new MemeProfile("", List.of(), List.of(), List.of(), "", List.of());
    }

    public boolean isComplete() {
        return !meaning.isBlank() && !usageExamples.isEmpty();
    }
}

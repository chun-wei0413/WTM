package com.wtm.application.report;

import java.util.List;

/**
 * What the vision model proposes after looking at a reported meme again.
 *
 * @param isMeme false when the model thinks the picture is not a meme at all
 * @param reasoning one or two sentences on what it changed and why, for the administrator
 */
public record Suggestion(boolean isMeme, String meaning, List<String> usageExamples, List<String> emotions,
                         List<String> tags, String imageText, String reasoning) {

    public Suggestion {
        meaning = meaning == null ? "" : meaning.strip();
        usageExamples = usageExamples == null ? List.of() : List.copyOf(usageExamples);
        emotions = emotions == null ? List.of() : List.copyOf(emotions);
        tags = tags == null ? List.of() : List.copyOf(tags);
        imageText = imageText == null ? "" : imageText.strip();
        reasoning = reasoning == null ? "" : reasoning.strip();
    }
}

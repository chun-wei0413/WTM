package com.wtm.application.report;

import java.util.List;

/**
 * What the vision model proposes after looking at a reported meme again.
 *
 * @param isMeme false when the model thinks the picture is not a meme at all
 * @param reasoning one or two sentences on what it changed and why, for the administrator
 * @param keep true when the model found the current description right and proposes no change
 */
public record Suggestion(boolean isMeme, String meaning, List<String> usageExamples, List<String> emotions,
                         List<String> tags, String imageText, String reasoning, boolean keep) {

    public Suggestion {
        meaning = meaning == null ? "" : meaning.strip();
        usageExamples = usageExamples == null ? List.of() : List.copyOf(usageExamples);
        emotions = emotions == null ? List.of() : List.copyOf(emotions);
        tags = tags == null ? List.of() : List.copyOf(tags);
        imageText = imageText == null ? "" : imageText.strip();
        reasoning = reasoning == null ? "" : reasoning.strip();
    }

    /** A proposal to change the description. */
    public Suggestion(boolean isMeme, String meaning, List<String> usageExamples, List<String> emotions,
                      List<String> tags, String imageText, String reasoning) {
        this(isMeme, meaning, usageExamples, emotions, tags, imageText, reasoning, false);
    }

    /** Whether the proposal is a description that could replace the current one. */
    public boolean isUsableDescription() {
        return isMeme && !meaning.isBlank() && !usageExamples.isEmpty();
    }
}

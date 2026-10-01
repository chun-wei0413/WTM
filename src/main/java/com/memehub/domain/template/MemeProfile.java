package com.memehub.domain.template;

import java.util.List;

/**
 * What a template means and when to use it. This is the knowledge that makes a
 * template findable, independent of how many slots it has.
 */
public record MemeProfile(String meaning, List<String> usageExamples,
                          List<String> emotions, List<String> aliases) {

    public MemeProfile {
        meaning = meaning == null ? "" : meaning.strip();
        usageExamples = usageExamples == null ? List.of() : List.copyOf(usageExamples);
        emotions = emotions == null ? List.of() : List.copyOf(emotions);
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }

    public static MemeProfile empty() {
        return new MemeProfile("", List.of(), List.of(), List.of());
    }

    public boolean isComplete() {
        return !meaning.isBlank() && !usageExamples.isEmpty();
    }
}

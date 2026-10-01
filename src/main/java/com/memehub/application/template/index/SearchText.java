package com.memehub.application.template.index;

import com.memehub.application.port.out.SearchIndexPort.IndexableTemplate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the text that represents a template in search: its names and everything
 * that describes when it is used. This text is both embedded and keyword-matched.
 */
public final class SearchText {

    private SearchText() {
    }

    public static String of(IndexableTemplate template) {
        var profile = template.profile();
        List<String> parts = new ArrayList<>();
        parts.add(template.name());
        parts.addAll(profile.aliases());
        parts.add(profile.meaning());
        parts.addAll(profile.usageExamples());
        parts.addAll(profile.emotions());
        return String.join("\n", parts.stream().filter(p -> p != null && !p.isBlank()).toList());
    }
}

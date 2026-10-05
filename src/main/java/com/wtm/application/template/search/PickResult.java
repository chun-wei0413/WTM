package com.wtm.application.template.search;

import java.util.List;

/**
 * The answer to "pick a meme for this situation".
 *
 * @param chosen the closest search result, or {@code null} when the library has nothing to offer at all
 * @param reason a language model's note on why it fits (or does not), or {@code null} when the model could not be asked
 * @param others the other search results, closest first
 */
public record PickResult(SearchResult chosen, String reason, List<SearchResult> others) {

    static PickResult nothing() {
        return new PickResult(null, null, List.of());
    }
}

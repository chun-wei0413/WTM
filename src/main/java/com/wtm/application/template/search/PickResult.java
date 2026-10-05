package com.wtm.application.template.search;

import java.util.List;

/**
 * The answer to "pick a meme for this situation".
 *
 * @param chosen the meme that fits best, or {@code null} when the library has nothing to offer at all
 * @param reason why it fits, or {@code null} when the model could not be asked (then {@code chosen} is simply the
 *               closest search result)
 * @param others the remaining candidates, closest first
 */
public record PickResult(SearchResult chosen, String reason, List<SearchResult> others) {

    static PickResult nothing() {
        return new PickResult(null, null, List.of());
    }
}

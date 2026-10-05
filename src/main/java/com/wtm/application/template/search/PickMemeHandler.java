package com.wtm.application.template.search;

import com.wtm.application.TooManyRequestsException;
import com.wtm.application.port.out.LlmUnavailableException;
import com.wtm.application.port.out.MemePickerPort;
import com.wtm.application.port.out.MemePickerPort.Candidate;
import com.wtm.application.port.out.MemePickerPort.Pick;
import com.wtm.application.port.out.RateLimiterPort;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "Pick a meme for me": search finds the closest candidates, then a language model reads their descriptions and
 * chooses one and says why. The model only chooses among what the search found, so it cannot invent a meme, and if
 * it cannot be reached the closest search result is offered without a reason.
 */
public class PickMemeHandler {

    private static final Logger log = LoggerFactory.getLogger(PickMemeHandler.class);

    /** How many search results the model gets to choose from. */
    static final int CANDIDATES = 8;
    static final int MAX_SITUATION_LENGTH = 300;
    /** The model shares one graphics card with tagging, so each person may ask only so often. */
    static final int PICKS_PER_WINDOW = 10;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final SearchTemplatesHandler search;
    private final MemePickerPort picker;
    private final RateLimiterPort limiter;

    public PickMemeHandler(SearchTemplatesHandler search, MemePickerPort picker, RateLimiterPort limiter) {
        this.search = search;
        this.picker = picker;
        this.limiter = limiter;
    }

    /**
     * @param userKey who is asking, for the rate limit
     * @throws TooManyRequestsException when this person has asked too often in the last minute
     */
    public PickResult handle(String userKey, String situation) {
        String text = situation == null ? "" : situation.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Describe the situation you want a meme for");
        }
        if (text.length() > MAX_SITUATION_LENGTH) {
            throw new IllegalArgumentException(
                    "The situation is too long: at most " + MAX_SITUATION_LENGTH + " characters");
        }
        if (!limiter.tryAcquire("pick:" + userKey, PICKS_PER_WINDOW, WINDOW)) {
            throw new TooManyRequestsException("Too many picks in a short time. Please try again in a minute.", WINDOW);
        }

        List<SearchResult> found = search.handle(text, CANDIDATES);
        if (found.isEmpty()) {
            return PickResult.nothing();
        }

        int index = 0;
        String reason = null;
        try {
            Pick pick = picker.pick(text, found.stream().map(PickMemeHandler::candidate).toList());
            if (pick.index() < 0 || pick.index() >= found.size()) {
                throw new LlmUnavailableException("The model chose candidate " + pick.index() + " of " + found.size());
            }
            index = pick.index();
            reason = pick.reason() == null || pick.reason().isBlank() ? null : pick.reason().strip();
        } catch (LlmUnavailableException e) {
            log.warn("Could not ask the model to pick, offering the closest result: {}", e.getMessage());
        }

        List<SearchResult> others = new ArrayList<>(found);
        SearchResult chosen = others.remove(index);
        return new PickResult(chosen, reason, List.copyOf(others));
    }

    private static Candidate candidate(SearchResult r) {
        return new Candidate(r.name(), r.meaning(), r.usageExamples(), r.emotions(), r.tags(), r.imageText());
    }
}

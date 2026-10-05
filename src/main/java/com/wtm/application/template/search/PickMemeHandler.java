package com.wtm.application.template.search;

import com.wtm.application.TooManyRequestsException;
import com.wtm.application.port.out.LlmUnavailableException;
import com.wtm.application.port.out.MemeExplainerPort;
import com.wtm.application.port.out.MemeExplainerPort.Meme;
import com.wtm.application.port.out.RateLimiterPort;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "Pick a meme for me": search finds the closest memes and the closest one is offered, with a language model's
 * note on why it fits (or does not). The model explains; it does not choose, because on the evaluation of the real
 * library its choices were worse than the search order (decision 22). If the model cannot be reached the meme is
 * offered without a note.
 */
public class PickMemeHandler {

    private static final Logger log = LoggerFactory.getLogger(PickMemeHandler.class);

    /** How many search results are returned: the closest one is the pick, the rest are alternatives. */
    static final int CANDIDATES = 8;
    static final int MAX_SITUATION_LENGTH = 300;
    /** The model shares one graphics card with tagging, so each person may ask only so often. */
    static final int PICKS_PER_WINDOW = 10;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final SearchTemplatesHandler search;
    private final MemeExplainerPort explainer;
    private final RateLimiterPort limiter;

    public PickMemeHandler(SearchTemplatesHandler search, MemeExplainerPort explainer, RateLimiterPort limiter) {
        this.search = search;
        this.explainer = explainer;
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

        SearchResult closest = found.get(0);
        String reason = null;
        try {
            String said = explainer.explain(text, meme(closest));
            reason = said == null || said.isBlank() ? null : said.strip();
        } catch (LlmUnavailableException e) {
            log.warn("Could not ask the model for a reason, offering the closest result without one: {}", e.getMessage());
        }
        return new PickResult(closest, reason, List.copyOf(found.subList(1, found.size())));
    }

    private static Meme meme(SearchResult r) {
        return new Meme(r.name(), r.meaning(), r.usageExamples(), r.emotions(), r.tags(), r.imageText());
    }
}

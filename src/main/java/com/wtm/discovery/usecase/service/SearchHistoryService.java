package com.wtm.discovery.usecase.service;

import com.wtm.discovery.usecase.port.in.command.SearchHistoryUseCase;
import com.wtm.discovery.usecase.port.in.query.HotTerm;
import com.wtm.discovery.usecase.port.out.searchlog.SearchLogPort;
import com.wtm.shared.UserId;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Keeps track of what people search for and offers the most common phrases as shortcuts.
 */
public class SearchHistoryService implements SearchHistoryUseCase {

    static final int MIN_LENGTH = 2;
    /** A whole sentence is not a shortcut, and it may say more about the person than they meant to share. */
    static final int MAX_LENGTH = 30;
    static final Duration WINDOW = Duration.ofDays(7);

    private final SearchLogPort log;
    private final Clock clock;

    public SearchHistoryService(SearchLogPort log, Clock clock) {
        this.log = log;
        this.clock = clock;
    }

    /** Remembers a search that found something; phrases that are too short or too long are ignored. */
    @Override
    public void record(UserId userId, String query) {
        String term = normalize(query);
        if (term.length() >= MIN_LENGTH && term.length() <= MAX_LENGTH) {
            log.record(userId, term);
        }
    }

    @Override
    public List<HotTerm> hot(int limit) {
        return log.hot(clock.instant().minus(WINDOW), Math.min(Math.max(limit, 1), 20));
    }

    /** The same phrase typed with other capitals or extra spaces counts as one. */
    static String normalize(String query) {
        return query == null ? "" : query.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}

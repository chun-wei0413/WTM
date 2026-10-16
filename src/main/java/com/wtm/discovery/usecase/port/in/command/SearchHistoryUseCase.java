package com.wtm.discovery.usecase.port.in.command;

import com.wtm.discovery.usecase.port.in.query.HotTerm;
import java.util.List;
import java.util.UUID;

public interface SearchHistoryUseCase {

    /** Remembers a search that found something; phrases that are too short or too long are ignored. */
    void record(UUID userId, String query);

    List<HotTerm> hot(int limit);
}

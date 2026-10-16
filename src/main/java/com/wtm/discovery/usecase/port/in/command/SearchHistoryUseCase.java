package com.wtm.discovery.usecase.port.in.command;

import com.wtm.discovery.usecase.port.in.query.HotTerm;
import com.wtm.shared.UserId;
import java.util.List;

public interface SearchHistoryUseCase {

    /** Remembers a search that found something; phrases that are too short or too long are ignored. */
    void record(UserId userId, String query);

    List<HotTerm> hot(int limit);
}

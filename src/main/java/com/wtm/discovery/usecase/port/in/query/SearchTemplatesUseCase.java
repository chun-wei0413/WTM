package com.wtm.discovery.usecase.port.in.query;

import java.util.List;

public interface SearchTemplatesUseCase {

    List<SearchResult> handle(Input input);

    record Input(String query, int limit) {
    }
}

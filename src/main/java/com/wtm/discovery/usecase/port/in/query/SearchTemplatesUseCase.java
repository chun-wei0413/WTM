package com.wtm.discovery.usecase.port.in.query;

import java.util.List;

public interface SearchTemplatesUseCase {

    List<SearchResult> handle(String query, int limit);
}

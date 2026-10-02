package com.wtm.adapter.in.web;

import com.wtm.application.library.SearchHistoryHandler;
import com.wtm.application.template.search.SearchResult;
import com.wtm.application.template.search.SearchTemplatesHandler;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/templates")
class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final SearchTemplatesHandler search;
    private final SearchHistoryHandler history;

    SearchController(SearchTemplatesHandler search, SearchHistoryHandler history) {
        this.search = search;
        this.history = history;
    }

    @GetMapping("/search")
    List<SearchResult> search(@RequestParam("q") String query,
                              @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit,
                              @AuthenticationPrincipal Jwt jwt) {
        List<SearchResult> results = search.handle(query, limit);
        if (!results.isEmpty()) {
            try {
                history.record(UUID.fromString(jwt.getSubject()), query);
            } catch (RuntimeException e) {
                // Keeping a tally must never make a search fail.
                log.warn("Could not record the search: {}", e.getMessage());
            }
        }
        return results;
    }
}

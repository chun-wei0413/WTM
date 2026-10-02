package com.wtm.adapter.in.web;

import com.wtm.application.template.search.SearchResult;
import com.wtm.application.template.search.SearchTemplatesHandler;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/templates")
class SearchController {

    private final SearchTemplatesHandler search;

    SearchController(SearchTemplatesHandler search) {
        this.search = search;
    }

    @GetMapping("/search")
    List<SearchResult> search(@RequestParam("q") String query,
                              @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return search.handle(query, limit);
    }
}

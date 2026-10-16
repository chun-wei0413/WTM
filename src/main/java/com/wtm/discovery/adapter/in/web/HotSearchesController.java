package com.wtm.discovery.adapter.in.web;

import com.wtm.discovery.usecase.port.in.command.SearchHistoryUseCase;
import com.wtm.discovery.usecase.port.in.query.HotTerm;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HotSearchesController {

    private final SearchHistoryUseCase history;

    HotSearchesController(SearchHistoryUseCase history) {
        this.history = history;
    }

    @GetMapping("/api/library/hot-searches")
    List<HotTerm> hotSearches(@RequestParam(defaultValue = "8") @Min(1) @Max(20) int limit) {
        return history.hot(limit);
    }
}

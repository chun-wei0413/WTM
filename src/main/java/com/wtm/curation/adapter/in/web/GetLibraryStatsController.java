package com.wtm.curation.adapter.in.web;

import com.wtm.curation.usecase.port.in.query.GetLibraryStatsUseCase;
import com.wtm.curation.usecase.port.in.query.LibraryStats;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class GetLibraryStatsController {

    private final GetLibraryStatsUseCase stats;

    GetLibraryStatsController(GetLibraryStatsUseCase stats) {
        this.stats = stats;
    }

    @GetMapping("/api/admin/collection/status")
    LibraryStats status() {
        return stats.handle();
    }
}

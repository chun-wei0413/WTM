package com.wtm.discovery.adapter.in.web;

import com.wtm.discovery.adapter.in.scheduling.IndexProperties;
import com.wtm.discovery.usecase.port.in.command.SyncSearchIndexUseCase;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets an administrator run the search index sync immediately instead of waiting
 * for the next scheduled round.
 */
@RestController
@RequestMapping("/api/admin/index")
class AdminIndexController {

    private final SyncSearchIndexUseCase sync;
    private final IndexProperties properties;

    AdminIndexController(SyncSearchIndexUseCase sync, IndexProperties properties) {
        this.sync = sync;
        this.properties = properties;
    }

    @PostMapping("/sync")
    SyncSearchIndexUseCase.Result sync() {
        return sync.handle(properties.batchSize());
    }
}

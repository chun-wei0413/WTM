package com.usethatmeme.adapter.in.web;

import com.usethatmeme.adapter.scheduling.IndexProperties;
import com.usethatmeme.application.template.index.SyncSearchIndexHandler;
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

    private final SyncSearchIndexHandler sync;
    private final IndexProperties properties;

    AdminIndexController(SyncSearchIndexHandler sync, IndexProperties properties) {
        this.sync = sync;
        this.properties = properties;
    }

    @PostMapping("/sync")
    SyncSearchIndexHandler.Result sync() {
        return sync.handle(properties.batchSize());
    }
}

package com.usethatmeme.application.collection;

import com.usethatmeme.application.port.out.CollectionRunPort;
import com.usethatmeme.application.port.out.CollectionRunPort.RunView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ListCollectionRunsHandler {

    private final CollectionRunPort runs;

    public ListCollectionRunsHandler(CollectionRunPort runs) {
        this.runs = runs;
    }

    public List<RunView> recent(int limit) {
        return runs.recent(Math.max(1, Math.min(limit, 50)));
    }

    public Optional<RunView> find(UUID id) {
        return runs.find(id);
    }
}

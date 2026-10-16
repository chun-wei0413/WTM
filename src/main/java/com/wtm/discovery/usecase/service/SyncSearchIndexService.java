package com.wtm.discovery.usecase.service;

import com.wtm.discovery.usecase.port.in.command.SyncSearchIndexUseCase;
import com.wtm.discovery.usecase.port.out.embedding.EmbeddingPort;
import com.wtm.discovery.usecase.port.out.search.SearchIndexPort;
import com.wtm.discovery.usecase.port.out.search.SearchIndexPort.IndexableTemplate;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Brings the search index in line with the templates. It is safe to run at any
 * time and any number of times: whatever fails this round is simply picked up
 * by the next one.
 */
public class SyncSearchIndexService implements SyncSearchIndexUseCase {

    private static final Logger log = LoggerFactory.getLogger(SyncSearchIndexService.class);

    private final SearchIndexPort index;
    private final EmbeddingPort embeddings;

    public SyncSearchIndexService(SearchIndexPort index, EmbeddingPort embeddings) {
        this.index = index;
        this.embeddings = embeddings;
    }

    @Override
    public Result handle(int batchSize) {
        int removed = 0;
        for (UUID id : index.findIndexEntriesToRemove(batchSize)) {
            index.remove(id);
            removed++;
        }

        int indexed = 0;
        int failed = 0;
        for (UUID id : index.findTemplatesNeedingIndex(batchSize)) {
            try {
                if (indexOne(id)) {
                    indexed++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("Could not index template {}; it will be retried: {}", id, e.getMessage());
            }
        }
        return new Result(indexed, removed, failed);
    }

    private boolean indexOne(UUID id) {
        Optional<IndexableTemplate> loaded = index.load(id);
        if (loaded.isEmpty() || !"APPROVED".equals(loaded.get().status())) {
            return false;
        }
        IndexableTemplate template = loaded.get();
        String text = SearchText.of(template);
        float[] embedding = embeddings.embed(text);
        index.upsert(template.id(), text, embedding, template.slots(), template.updatedAt());
        return true;
    }

}

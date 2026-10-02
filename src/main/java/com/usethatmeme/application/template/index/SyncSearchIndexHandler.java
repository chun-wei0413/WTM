package com.usethatmeme.application.template.index;

import com.usethatmeme.application.port.out.EmbeddingPort;
import com.usethatmeme.application.port.out.SearchIndexPort;
import com.usethatmeme.application.port.out.SearchIndexPort.IndexableTemplate;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Brings the search index in line with the templates. It is safe to run at any
 * time and any number of times: whatever fails this round is simply picked up
 * by the next one.
 */
public class SyncSearchIndexHandler {

    private static final Logger log = LoggerFactory.getLogger(SyncSearchIndexHandler.class);

    private final SearchIndexPort index;
    private final EmbeddingPort embeddings;

    public SyncSearchIndexHandler(SearchIndexPort index, EmbeddingPort embeddings) {
        this.index = index;
        this.embeddings = embeddings;
    }

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

    public record Result(int indexed, int removed, int failed) {
    }
}

package com.wtm.application.template.search;

import com.wtm.application.port.out.TemplateSearchPort;
import com.wtm.application.port.out.TemplateSearchPort.Hit;
import java.util.List;

/**
 * Keyword search over the library, ranked by {@link Bm25Index}. The index is kept in memory and built again only when the
 * searchable texts have changed (a meme added, edited or withdrawn), which for a library of thousands takes milliseconds.
 */
public class KeywordSearch {

    private record Snapshot(String version, Bm25Index index) {
    }

    private final TemplateSearchPort port;
    private volatile Snapshot snapshot;

    public KeywordSearch(TemplateSearchPort port) {
        this.port = port;
    }

    public List<Hit> search(String query, int limit) {
        return current().search(query, limit);
    }

    private Bm25Index current() {
        String version = port.searchTextsVersion();
        Snapshot seen = snapshot;
        if (seen != null && seen.version().equals(version)) {
            return seen.index();
        }
        synchronized (this) {
            seen = snapshot;
            if (seen == null || !seen.version().equals(version)) {
                // the version is read before the texts, so a change in between only makes the next search build again
                seen = new Snapshot(version, new Bm25Index(port.searchTexts()));
                snapshot = seen;
            }
            return seen.index();
        }
    }
}

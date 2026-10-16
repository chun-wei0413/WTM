package com.wtm.discovery.adapter.out.search;

import com.wtm.discovery.entity.Hit;
import com.wtm.discovery.usecase.port.out.search.KeywordSearchPort;
import com.wtm.discovery.usecase.port.out.search.TemplateSearchPort;
import com.wtm.discovery.usecase.port.out.search.TemplateSearchPort.IndexedText;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Keyword ranking by BM25 with Apache Lucene, over the searchable text of every published meme.
 *
 * <p>The index lives in memory. When the library changes, only the memes whose text changed are written again and the
 * ones that left are removed; the rest of the index stays as it is.
 */
@Component
class LuceneKeywordSearch implements KeywordSearchPort {

    private static final Logger log = LoggerFactory.getLogger(LuceneKeywordSearch.class);
    private static final String ID = "id";
    private static final String TEXT = "text";

    private final TemplateSearchPort source;
    private final Analyzer analyzer;
    private final ByteBuffersDirectory directory = new ByteBuffersDirectory();
    private final IndexWriter writer;
    private final SearcherManager searchers;
    /** What the index holds now, so that a change in the library is turned into the few writes it needs. */
    private final Map<UUID, String> indexed = new HashMap<>();
    private String version;

    @Autowired
    LuceneKeywordSearch(TemplateSearchPort source) {
        // no English stop words: "the" and "it" can be the whole point of a meme's name
        this(source, new CJKAnalyzer(CharArraySet.EMPTY_SET));
    }

    LuceneKeywordSearch(TemplateSearchPort source, Analyzer analyzer) {
        this.source = source;
        this.analyzer = analyzer;
        try {
            IndexWriterConfig config = new IndexWriterConfig(analyzer).setSimilarity(new BM25Similarity());
            this.writer = new IndexWriter(directory, config);
            this.searchers = new SearcherManager(writer, new SearcherFactory() {
                @Override
                public IndexSearcher newSearcher(IndexReader reader, IndexReader previous) {
                    IndexSearcher searcher = new IndexSearcher(reader);
                        return searcher;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<Hit> search(String query, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        update();
        Set<String> terms = terms(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (String term : terms) {
            builder.add(new TermQuery(new Term(TEXT, term)), BooleanClause.Occur.SHOULD);
        }
        try {
            IndexSearcher searcher = searchers.acquire();
            try {
                StoredFields stored = searcher.storedFields();
                ScoreDoc[] top = searcher.search(builder.build(), limit).scoreDocs;
                ArrayList<Hit> hits = new ArrayList<>(top.length);
                for (ScoreDoc doc : top) {
                    hits.add(new Hit(UUID.fromString(stored.document(doc.doc).get(ID)), doc.score));
                }
                return List.copyOf(hits);
            } finally {
                searchers.release(searcher);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Brings the index up to date with the library, when the library has changed since the last time. */
    private synchronized void update() {
        // the version is read before the texts, so a change in between only makes the next search look again
        String current = source.searchTextsVersion();
        if (current.equals(version)) {
            return;
        }
        try {
            List<IndexedText> texts = source.searchTexts();
            Set<UUID> present = new HashSet<>();
            int written = 0;
            for (IndexedText text : texts) {
                present.add(text.id());
                if (!text.text().equals(indexed.get(text.id()))) {
                    writer.updateDocument(new Term(ID, text.id().toString()), document(text));
                    indexed.put(text.id(), text.text());
                    written++;
                }
            }
            Set<UUID> gone = new HashSet<>(indexed.keySet());
            gone.removeAll(present);
            for (UUID id : gone) {
                writer.deleteDocuments(new Term(ID, id.toString()));
                indexed.remove(id);
            }
            if (written > 0 || !gone.isEmpty()) {
                searchers.maybeRefreshBlocking();
                log.info("Keyword index updated: {} written, {} removed, {} in all", written, gone.size(), indexed.size());
            }
            version = current;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Document document(IndexedText text) {
        Document document = new Document();
        document.add(new StringField(ID, text.id().toString(), Field.Store.YES));
        document.add(new TextField(TEXT, text.text(), Field.Store.NO));
        return document;
    }

    /** The distinct words of the query, cut the way the texts were cut. */
    private Set<String> terms(String query) {
        Set<String> terms = new LinkedHashSet<>();
        if (query == null) {
            return terms;
        }
        try (var stream = analyzer.tokenStream(TEXT, query)) {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                terms.add(term.toString());
            }
            stream.end();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return terms;
    }

    @PreDestroy
    void close() throws IOException {
        searchers.close();
        writer.close();
        directory.close();
        analyzer.close();
    }
}

package com.wtm.discovery.usecase.service;

import com.wtm.discovery.entity.ConvexFusion;
import com.wtm.discovery.usecase.port.in.query.SearchResult;
import com.wtm.discovery.usecase.port.in.query.SearchTemplatesUseCase;
import com.wtm.discovery.usecase.port.out.embedding.EmbeddingPort;
import com.wtm.discovery.usecase.port.out.search.KeywordSearchPort;
import com.wtm.discovery.usecase.port.out.search.TemplateSearchPort;
import com.wtm.discovery.entity.Hit;
import com.wtm.discovery.usecase.port.out.search.TemplateSearchPort.SearchCard;
import com.wtm.shared.port.ObjectStoragePort;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hybrid search: the semantic (vector) ranking and the keyword ranking (BM25) merged by a weighted sum of their scores.
 * If the embedding service is down, keyword search alone still answers.
 */
public class SearchTemplatesService implements SearchTemplatesUseCase {

    private static final Logger log = LoggerFactory.getLogger(SearchTemplatesService.class);
    private static final Duration IMAGE_URL_TTL = Duration.ofMinutes(15);
    private static final int CANDIDATES_PER_RANKING = 20;

    /** The weight of the semantic ranking when none is given: chosen on half of the evaluation queries, checked on the rest. */
    public static final double DEFAULT_DENSE_WEIGHT = 0.7;

    private final TemplateSearchPort search;
    private final KeywordSearchPort keywords;
    private final EmbeddingPort embeddings;
    private final ObjectStoragePort storage;
    private final double denseWeight;

    public SearchTemplatesService(TemplateSearchPort search, KeywordSearchPort keywords, EmbeddingPort embeddings,
                                  ObjectStoragePort storage, double denseWeight) {
        if (denseWeight < 0 || denseWeight > 1) {
            throw new IllegalArgumentException("The semantic weight must be between 0 and 1");
        }
        this.search = search;
        this.keywords = keywords;
        this.embeddings = embeddings;
        this.storage = storage;
        this.denseWeight = denseWeight;
    }

    @Override
    public List<SearchResult> handle(String query, int limit) {
        String text = query == null ? "" : query.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Search text must not be blank");
        }
        int candidates = Math.max(CANDIDATES_PER_RANKING, limit * 2);

        List<Hit> semantic = semanticRanking(text, candidates);
        List<Hit> keyword = keywords.search(text, candidates);

        List<Hit> fused = ConvexFusion.fuse(semantic, keyword, denseWeight, limit);
        Map<UUID, SearchCard> cards = search.cards(fused.stream().map(Hit::id).toList()).stream()
                .collect(Collectors.toMap(SearchCard::id, Function.identity()));

        return fused.stream()
                .filter(s -> cards.containsKey(s.id()))
                .map(s -> {
                    SearchCard card = cards.get(s.id());
                    return new SearchResult(card.id(), card.name(), s.score(),
                            storage.presignedGetUrl(card.imageKey(), IMAGE_URL_TTL), card.imageWidth(),
                            card.imageHeight(), card.slots(),
                            card.meaning(), card.usageExamples(), card.emotions(), card.tags(), card.imageText(),
                            card.sourceType(), card.sourceUrl(), card.attribution(), card.reference());
                })
                .toList();
    }

    private List<Hit> semanticRanking(String text, int limit) {
        try {
            return search.byVector(embeddings.embed(text), limit);
        } catch (RuntimeException e) {
            log.warn("Semantic search unavailable, using keyword search only: {}", e.getMessage());
            return List.of();
        }
    }
}

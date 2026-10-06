package com.wtm.application.template.search;

import com.wtm.application.port.out.EmbeddingPort;
import com.wtm.application.port.out.ObjectStoragePort;
import com.wtm.application.port.out.TemplateSearchPort;
import com.wtm.application.port.out.TemplateSearchPort.SearchCard;
import com.wtm.application.template.search.ReciprocalRankFusion.Scored;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hybrid search: semantic (vector) and keyword rankings merged with
 * reciprocal rank fusion. If the embedding service is down, keyword search
 * alone still answers.
 */
public class SearchTemplatesHandler {

    private static final Logger log = LoggerFactory.getLogger(SearchTemplatesHandler.class);
    private static final Duration IMAGE_URL_TTL = Duration.ofMinutes(15);
    private static final int CANDIDATES_PER_RANKING = 20;

    private final TemplateSearchPort search;
    private final EmbeddingPort embeddings;
    private final ObjectStoragePort storage;

    public SearchTemplatesHandler(TemplateSearchPort search, EmbeddingPort embeddings,
                                  ObjectStoragePort storage) {
        this.search = search;
        this.embeddings = embeddings;
        this.storage = storage;
    }

    public List<SearchResult> handle(String query, int limit) {
        String text = query == null ? "" : query.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Search text must not be blank");
        }
        int candidates = Math.max(CANDIDATES_PER_RANKING, limit * 2);

        List<UUID> semantic = semanticRanking(text, candidates);
        List<UUID> keyword = search.byKeyword(text, candidates);

        List<Scored> fused = ReciprocalRankFusion.fuse(List.of(semantic, keyword), limit);
        Map<UUID, SearchCard> cards = search.cards(fused.stream().map(Scored::id).toList()).stream()
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

    private List<UUID> semanticRanking(String text, int limit) {
        try {
            return search.byVector(embeddings.embed(text), limit);
        } catch (RuntimeException e) {
            log.warn("Semantic search unavailable, using keyword search only: {}", e.getMessage());
            return List.of();
        }
    }
}

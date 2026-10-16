package com.wtm.discovery.entity;

import com.wtm.discovery.entity.Hit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Merges the semantic and the keyword ranking by a weighted sum of their scores, each first scaled to 0..1 within its own
 * list (the best of a list becomes 1, the worst 0). A meme missing from a list gets 0 from it. Bruch, Gai and Ingber (2022)
 * found this to beat reciprocal rank fusion for hybrid search, and it did on this library too (docs/EVALUATION.zh-TW.md).
 */
public final class ConvexFusion {

    private ConvexFusion() {
    }

    /**
     * @param denseWeight the weight of the semantic ranking, between 0 and 1; the keyword ranking gets the rest
     */
    public static List<Hit> fuse(List<Hit> dense, List<Hit> keyword, double denseWeight, int limit) {
        Map<UUID, Double> semantic = scaled(dense);
        Map<UUID, Double> words = scaled(keyword);
        Set<UUID> order = new LinkedHashSet<>();
        dense.forEach(h -> order.add(h.id()));
        keyword.forEach(h -> order.add(h.id()));
        return order.stream()
                .map(id -> new Hit(id, denseWeight * semantic.getOrDefault(id, 0.0)
                        + (1 - denseWeight) * words.getOrDefault(id, 0.0)))
                .sorted(Comparator.comparingDouble(Hit::score).reversed())   // stable: ties keep the semantic order
                .limit(limit)
                .toList();
    }

    private static Map<UUID, Double> scaled(List<Hit> hits) {
        Map<UUID, Double> scaled = new HashMap<>();
        if (hits.isEmpty()) {
            return scaled;
        }
        double low = hits.stream().mapToDouble(Hit::score).min().orElse(0);
        double high = hits.stream().mapToDouble(Hit::score).max().orElse(0);
        for (Hit hit : hits) {
            scaled.put(hit.id(), high > low ? (hit.score() - low) / (high - low) : 1.0);
        }
        return scaled;
    }
}

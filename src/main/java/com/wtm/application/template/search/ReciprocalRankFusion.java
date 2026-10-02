package com.wtm.application.template.search;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Merges several ranked lists into one. An item scores 1 / (k + rank) in every
 * list it appears in, so items that rank well in more than one list rise to the top
 * without having to compare scores from different scales (distance vs similarity).
 */
public final class ReciprocalRankFusion {

    public static final int DEFAULT_K = 60;

    private ReciprocalRankFusion() {
    }

    public static List<Scored> fuse(List<List<UUID>> rankings, int limit) {
        return fuse(rankings, limit, DEFAULT_K);
    }

    public static List<Scored> fuse(List<List<UUID>> rankings, int limit, int k) {
        Map<UUID, Double> scores = new LinkedHashMap<>();
        for (List<UUID> ranking : rankings) {
            for (int i = 0; i < ranking.size(); i++) {
                scores.merge(ranking.get(i), 1.0 / (k + i + 1), Double::sum);
            }
        }
        return scores.entrySet().stream()
                .map(e -> new Scored(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(limit)
                .toList();
    }

    public record Scored(UUID id, double score) {
    }
}

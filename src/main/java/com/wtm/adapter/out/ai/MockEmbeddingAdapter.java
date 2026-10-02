package com.wtm.adapter.out.ai;

import com.wtm.application.port.out.EmbeddingPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic, dependency-free embedding: hashes character bigrams into a
 * fixed-size vector and normalizes it. Texts that share substrings end up with
 * similar vectors, which is enough to exercise search code without a model.
 */
@Component
@ConditionalOnProperty(name = "wtm.embedding.provider", havingValue = "mock", matchIfMissing = true)
class MockEmbeddingAdapter implements EmbeddingPort {

    private final int dimension;

    MockEmbeddingAdapter(EmbeddingProperties properties) {
        this.dimension = properties.dimension();
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimension];
        String normalized = text == null ? "" : text.strip().toLowerCase();
        if (normalized.length() == 1) {
            vector[Math.floorMod(normalized.hashCode(), dimension)] += 1f;
        }
        for (int i = 0; i + 1 < normalized.length(); i++) {
            int hash = 31 * normalized.charAt(i) + normalized.charAt(i + 1);
            vector[Math.floorMod(hash, dimension)] += 1f;
        }
        return normalize(vector);
    }

    private static float[] normalize(float[] vector) {
        double sum = 0;
        for (float v : vector) {
            sum += v * v;
        }
        if (sum == 0) {
            return vector;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
        return vector;
    }
}

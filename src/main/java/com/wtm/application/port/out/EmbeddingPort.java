package com.wtm.application.port.out;

/**
 * Outbound port for turning text into a vector. The vector size must match
 * the {@code template_search.embedding} column (1024, bge-m3).
 */
public interface EmbeddingPort {

    float[] embed(String text);
}

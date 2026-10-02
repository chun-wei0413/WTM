package com.usethatmeme.adapter.out.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("usethatmeme.embedding")
public record EmbeddingProperties(
        @DefaultValue("mock") String provider,
        @DefaultValue("1024") int dimension,
        @DefaultValue Ollama ollama) {

    public record Ollama(
            @DefaultValue("http://localhost:11434") String baseUrl,
            @DefaultValue("bge-m3") String model) {
    }
}

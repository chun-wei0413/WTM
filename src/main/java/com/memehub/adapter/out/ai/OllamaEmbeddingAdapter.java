package com.memehub.adapter.out.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.memehub.application.port.out.EmbeddingPort;
import com.memehub.application.port.out.LlmUnavailableException;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "memehub.embedding.provider", havingValue = "ollama")
class OllamaEmbeddingAdapter implements EmbeddingPort {

    private final RestClient client;
    private final String model;
    private final int dimension;

    OllamaEmbeddingAdapter(EmbeddingProperties properties) {
        var ollama = properties.ollama();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder()
                .baseUrl(ollama.baseUrl())
                .requestFactory(factory)
                .build();
        this.model = ollama.model();
        this.dimension = properties.dimension();
    }

    @Override
    public float[] embed(String text) {
        try {
            JsonNode response = client.post()
                    .uri("/api/embed")
                    .body(Map.of("model", model, "input", text))
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode first = response == null ? null : response.path("embeddings").path(0);
            if (first == null || !first.isArray() || first.size() != dimension) {
                throw new LlmUnavailableException("Ollama returned an embedding of unexpected size");
            }
            float[] vector = new float[dimension];
            for (int i = 0; i < dimension; i++) {
                vector[i] = (float) first.get(i).asDouble();
            }
            return vector;
        } catch (RestClientException e) {
            throw new LlmUnavailableException("Ollama embedding request failed: " + e.getMessage(), e);
        }
    }
}

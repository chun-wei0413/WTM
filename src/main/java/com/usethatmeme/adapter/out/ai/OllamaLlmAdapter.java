package com.usethatmeme.adapter.out.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.usethatmeme.application.port.out.LlmPort;
import com.usethatmeme.application.port.out.LlmUnavailableException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "usethatmeme.llm.provider", havingValue = "ollama")
class OllamaLlmAdapter implements LlmPort {

    private final RestClient client;
    private final String model;

    OllamaLlmAdapter(LlmProperties properties) {
        var ollama = properties.ollama();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(120));
        this.client = RestClient.builder()
                .baseUrl(ollama.baseUrl())
                .requestFactory(factory)
                .build();
        this.model = ollama.model();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));
        try {
            JsonNode response = client.post()
                    .uri("/api/chat")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.path("message").has("content")) {
                throw new LlmUnavailableException("Ollama returned an unexpected response");
            }
            return response.path("message").path("content").asText();
        } catch (RestClientException e) {
            throw new LlmUnavailableException("Ollama request failed: " + e.getMessage(), e);
        }
    }
}

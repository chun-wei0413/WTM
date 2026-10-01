package com.memehub.adapter.out.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("memehub.llm")
public record LlmProperties(
        @DefaultValue("mock") String provider,
        @DefaultValue Ollama ollama,
        @DefaultValue Mock mock) {

    public record Ollama(
            @DefaultValue("http://localhost:11434") String baseUrl,
            @DefaultValue("qwen2.5:7b") String model) {
    }

    public record Mock(
            @DefaultValue("200") long minLatencyMs,
            @DefaultValue("1500") long maxLatencyMs,
            @DefaultValue("0.0") double failureRate) {
    }
}

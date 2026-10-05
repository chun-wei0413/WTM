package com.wtm.adapter.out.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("wtm.explainer")
public record MemeExplainerProperties(
        @DefaultValue("mock") String provider,
        @DefaultValue Ollama ollama,
        @DefaultValue Mock mock) {

    public record Ollama(
            @DefaultValue("http://localhost:11434") String baseUrl,
            /** Reads only words, so the vision model can do it without a second model taking up the graphics card. */
            @DefaultValue("qwen2.5vl:7b") String model,
            /** Someone is waiting, but the first answer after a quiet spell also loads the model, which took 45 seconds once. */
            @DefaultValue("PT90S") Duration timeout) {
    }

    public record Mock(
            @DefaultValue("0") long latencyMs,
            @DefaultValue("0.0") double failureRate) {
    }
}

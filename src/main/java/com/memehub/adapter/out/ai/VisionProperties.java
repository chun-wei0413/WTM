package com.memehub.adapter.out.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("memehub.vision")
public record VisionProperties(
        @DefaultValue("mock") String provider,
        @DefaultValue Ollama ollama,
        @DefaultValue Mock mock) {

    public record Ollama(
            @DefaultValue("http://localhost:11434") String baseUrl,
            @DefaultValue("qwen2.5vl:7b") String model,
            /** Looking at a picture takes much longer than answering a question. */
            @DefaultValue("PT3M") Duration timeout,
            /** Pictures are shrunk to fit this many pixels on the long side before the model sees them. */
            @DefaultValue("1024") int maxSide) {
    }

    public record Mock(
            @DefaultValue("0") long latencyMs,
            @DefaultValue("0.0") double failureRate) {
    }
}

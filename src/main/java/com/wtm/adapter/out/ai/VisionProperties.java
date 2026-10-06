package com.wtm.adapter.out.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("wtm.vision")
public record VisionProperties(
        @DefaultValue("mock") String provider,
        @DefaultValue Ollama ollama,
        @DefaultValue Gemini gemini,
        @DefaultValue Mock mock) {

    public record Ollama(
            @DefaultValue("http://localhost:11434") String baseUrl,
            @DefaultValue("qwen2.5vl:7b") String model,
            /** Looking at a picture takes much longer than answering a question. */
            @DefaultValue("PT3M") Duration timeout,
            /** Pictures are shrunk to fit this many pixels on the long side before the model sees them. */
            @DefaultValue("1024") int maxSide) {
    }

    /**
     * Google's Gemini through its API. It knows far more memes than a small local model, so it can say where one
     * comes from. The picture leaves the machine, and on the free tier Google may use what it is sent to improve its
     * products, so use it for pictures that are already public.
     */
    public record Gemini(
            @DefaultValue("https://generativelanguage.googleapis.com") String baseUrl,
            @DefaultValue("gemini-3.5-flash-lite") String model,
            /** The key from Google AI Studio; read from the environment (GEMINI_API_KEY), never written in a file that is committed. */
            @DefaultValue("") String apiKey,
            @DefaultValue("PT60S") Duration timeout,
            @DefaultValue("1024") int maxSide,
            /** The shortest wait between two calls, so the free tier's per-minute limit is not hit by a queue of pictures. */
            @DefaultValue("PT5S") Duration minInterval) {
    }

    public record Mock(
            @DefaultValue("0") long latencyMs,
            @DefaultValue("0.0") double failureRate) {
    }
}

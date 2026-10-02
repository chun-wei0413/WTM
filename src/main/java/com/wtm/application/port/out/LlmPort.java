package com.wtm.application.port.out;

/**
 * Outbound port for text generation. Adapters: mock (tests, load testing) and Ollama.
 */
public interface LlmPort {

    String complete(String systemPrompt, String userPrompt);
}

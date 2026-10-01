package com.memehub.adapter.out.ai;

import com.memehub.application.port.out.LlmPort;
import com.memehub.application.port.out.LlmUnavailableException;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fake LLM with configurable latency and failure rate, so load tests measure
 * memehub itself instead of a GPU or a third-party rate limit.
 */
@Component
@ConditionalOnProperty(name = "memehub.llm.provider", havingValue = "mock", matchIfMissing = true)
class MockLlmAdapter implements LlmPort {

    private final LlmProperties.Mock settings;

    MockLlmAdapter(LlmProperties properties) {
        this.settings = properties.mock();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        simulateLatency();
        if (ThreadLocalRandom.current().nextDouble() < settings.failureRate()) {
            throw new LlmUnavailableException("Mock LLM failure (simulated)");
        }
        return "[mock] " + userPrompt;
    }

    private void simulateLatency() {
        long min = settings.minLatencyMs();
        long max = Math.max(min, settings.maxLatencyMs());
        long delay = min == max ? min : ThreadLocalRandom.current().nextLong(min, max + 1);
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmUnavailableException("Interrupted while waiting for mock LLM", e);
        }
    }
}

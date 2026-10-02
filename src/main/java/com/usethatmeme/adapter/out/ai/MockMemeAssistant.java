package com.usethatmeme.adapter.out.ai;

import com.usethatmeme.application.port.out.LlmUnavailableException;
import com.usethatmeme.application.port.out.MemeAssistantPort;
import com.usethatmeme.domain.template.Slot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stand-in for the language model with the same latency and failure knobs as the mock LLM.
 * Captions are deterministic: the first slot gets the situation, the others get their role.
 */
@Component
@ConditionalOnProperty(name = "usethatmeme.llm.provider", havingValue = "mock", matchIfMissing = true)
class MockMemeAssistant implements MemeAssistantPort {

    private final LlmProperties.Mock settings;

    MockMemeAssistant(LlmProperties properties) {
        this.settings = properties.mock();
    }

    @Override
    public Map<Integer, String> writeCaptions(String situation, CaptionBrief brief) {
        simulateLatency();
        if (ThreadLocalRandom.current().nextDouble() < settings.failureRate()) {
            throw new LlmUnavailableException("Mock model failure (simulated)");
        }
        Map<Integer, String> captions = new LinkedHashMap<>();
        boolean first = true;
        for (Slot slot : brief.slots()) {
            String text = first ? situation : slot.role();
            captions.put(slot.slotNo(), clip(text, slot.maxChars()));
            first = false;
        }
        return captions;
    }

    private static String clip(String text, int maxChars) {
        return text.codePointCount(0, text.length()) <= maxChars
                ? text
                : text.substring(0, text.offsetByCodePoints(0, maxChars));
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
            throw new LlmUnavailableException("Interrupted while waiting for the mock model", e);
        }
    }
}

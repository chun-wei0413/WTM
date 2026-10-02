package com.usethatmeme.adapter.out.ai;

import com.usethatmeme.application.collection.ImageTags;
import com.usethatmeme.application.port.out.LlmUnavailableException;
import com.usethatmeme.application.port.out.VisionTaggerPort;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stand-in for the vision model: always calls the picture a meme and describes it with fixed words,
 * with the same latency and failure knobs as the other mocks, so tests and load runs need no GPU.
 */
@Component
@ConditionalOnProperty(name = "usethatmeme.vision.provider", havingValue = "mock", matchIfMissing = true)
class MockVisionTagger implements VisionTaggerPort {

    private final VisionProperties.Mock settings;

    MockVisionTagger(VisionProperties properties) {
        this.settings = properties.mock();
    }

    @Override
    public ImageTags describe(byte[] image, String contentType, String hint) {
        if (settings.latencyMs() > 0) {
            try {
                Thread.sleep(settings.latencyMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmUnavailableException("Interrupted while waiting for the mock vision model", e);
            }
        }
        if (ThreadLocalRandom.current().nextDouble() < settings.failureRate()) {
            throw new LlmUnavailableException("Mock vision model failure (simulated)");
        }
        String title = hint == null || hint.isBlank() ? "模擬梗圖" : hint;
        return new ImageTags(true, title, "這是測試用的描述:" + title,
                List.of("測試情境一:" + title, "測試情境二"), List.of("測試"), List.of("mock", "測試"), "");
    }
}

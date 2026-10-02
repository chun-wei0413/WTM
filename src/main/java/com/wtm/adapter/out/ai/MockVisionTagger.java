package com.wtm.adapter.out.ai;

import com.wtm.application.collection.ImageTags;
import com.wtm.application.port.out.LlmUnavailableException;
import com.wtm.application.port.out.VisionTaggerPort;
import com.wtm.application.report.ReviewRequest;
import com.wtm.application.report.Suggestion;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stand-in for the vision model: always calls the picture a meme and describes it with fixed words,
 * with the same latency and failure knobs as the other mocks, so tests and load runs need no GPU.
 */
@Component
@ConditionalOnProperty(name = "wtm.vision.provider", havingValue = "mock", matchIfMissing = true)
class MockVisionTagger implements VisionTaggerPort {

    private final VisionProperties.Mock settings;

    MockVisionTagger(VisionProperties properties) {
        this.settings = properties.mock();
    }

    @Override
    public Suggestion reassess(byte[] image, String contentType, ReviewRequest request) {
        pretendToWork();
        // A complaint containing "[keep]" makes the mock find nothing wrong, so both outcomes can be tested.
        boolean keep = request.complaints().stream().anyMatch(c -> c.contains("[keep]"));
        return new Suggestion(true, "模擬重新分析:" + request.current().meaning(),
                List.of("模擬情境一", "模擬情境二"), List.of("測試"), List.of("mock", "重新分析"), "",
                "這是模擬的修改建議,依據 " + request.complaints().size() + " 則回報:" + String.join(" / ", request.complaints()),
                keep);
    }

    @Override
    public ImageTags describe(byte[] image, String contentType, String hint) {
        pretendToWork();
        String title = hint == null || hint.isBlank() ? "模擬梗圖" : hint;
        return new ImageTags(true, title, "這是測試用的描述:" + title,
                List.of("測試情境一:" + title, "測試情境二"), List.of("測試"), List.of("mock", "測試"), "");
    }

    private void pretendToWork() {
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
    }
}

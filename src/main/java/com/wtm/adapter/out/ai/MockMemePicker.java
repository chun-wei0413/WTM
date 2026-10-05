package com.wtm.adapter.out.ai;

import com.wtm.application.port.out.LlmUnavailableException;
import com.wtm.application.port.out.MemePickerPort;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stand-in for the language model: always chooses the first candidate and gives a reason made of its description,
 * with the same latency and failure knobs as the other mocks, so tests and load runs need no GPU.
 */
@Component
@ConditionalOnProperty(name = "wtm.picker.provider", havingValue = "mock", matchIfMissing = true)
class MockMemePicker implements MemePickerPort {

    private final MemePickerProperties.Mock settings;

    MockMemePicker(MemePickerProperties properties) {
        this.settings = properties.mock();
    }

    @Override
    public Pick pick(String situation, List<Candidate> candidates) {
        pretendToWork();
        Candidate first = candidates.get(0);
        return new Pick(0, "模擬推薦:" + first.name() + " 的意思是「" + first.meaning() + "」,所以適合「" + situation + "」這個處境。");
    }

    private void pretendToWork() {
        if (settings.latencyMs() > 0) {
            try {
                Thread.sleep(settings.latencyMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmUnavailableException("Interrupted while waiting for the mock picker", e);
            }
        }
        if (ThreadLocalRandom.current().nextDouble() < settings.failureRate()) {
            throw new LlmUnavailableException("Mock picker failure (simulated)");
        }
    }
}

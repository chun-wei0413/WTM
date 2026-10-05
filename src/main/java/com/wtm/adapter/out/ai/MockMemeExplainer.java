package com.wtm.adapter.out.ai;

import com.wtm.application.port.out.LlmUnavailableException;
import com.wtm.application.port.out.MemeExplainerPort;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stand-in for the language model: gives a reason made of the meme's description, with the same latency and failure
 * knobs as the other mocks, so tests and load runs need no GPU.
 */
@Component
@ConditionalOnProperty(name = "wtm.explainer.provider", havingValue = "mock", matchIfMissing = true)
class MockMemeExplainer implements MemeExplainerPort {

    private final MemeExplainerProperties.Mock settings;

    MockMemeExplainer(MemeExplainerProperties properties) {
        this.settings = properties.mock();
    }

    @Override
    public String explain(String situation, Meme meme) {
        pretendToWork();
        return "模擬推薦:" + meme.name() + " 的意思是「" + meme.meaning() + "」,所以適合「" + situation + "」這個處境。";
    }

    private void pretendToWork() {
        if (settings.latencyMs() > 0) {
            try {
                Thread.sleep(settings.latencyMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmUnavailableException("Interrupted while waiting for the mock explainer", e);
            }
        }
        if (ThreadLocalRandom.current().nextDouble() < settings.failureRate()) {
            throw new LlmUnavailableException("Mock explainer failure (simulated)");
        }
    }
}

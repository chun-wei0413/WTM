package com.wtm.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wtm.application.port.out.LlmUnavailableException;
import org.junit.jupiter.api.Test;

class MockAdaptersTest {

    @Test
    void mockEmbeddingIsDeterministicAndNormalized() {
        var adapter = new MockEmbeddingAdapter(
                new EmbeddingProperties("mock", 1024, new EmbeddingProperties.Ollama("http://x", "m")));

        float[] a = adapter.embed("老闆又改需求");
        float[] b = adapter.embed("老闆又改需求");

        assertThat(a).hasSize(1024).isEqualTo(b);
        double norm = 0;
        for (float v : a) {
            norm += v * v;
        }
        assertThat(Math.sqrt(norm)).isBetween(0.999, 1.001);
    }
}

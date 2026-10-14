package com.wtm.application.template.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.wtm.application.port.out.TemplateSearchPort.Hit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConvexFusionTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void eachListIsScaledToZeroAndOneBeforeTheyAreWeighed() {
        // the semantic scores are close together (0.50 to 0.52), the keyword scores are far apart: scaling makes them comparable
        List<Hit> fused = ConvexFusion.fuse(List.of(new Hit(a, 0.52), new Hit(b, 0.50)), List.of(new Hit(b, 12.0), new Hit(a, 2.0)), 0.5, 10);

        assertThat(fused).extracting(Hit::id).containsExactly(a, b);
        assertThat(fused.get(0).score()).isCloseTo(0.5, within(1e-9));    // a: 0.5 * 1 + 0.5 * 0
        assertThat(fused.get(1).score()).isCloseTo(0.5, within(1e-9));    // b: 0.5 * 0 + 0.5 * 1
    }

    @Test
    void theWeightDecidesWhichRankingWinsWhenTheyDisagree() {
        List<Hit> dense = List.of(new Hit(a, 0.9), new Hit(b, 0.1));
        List<Hit> words = List.of(new Hit(b, 5.0), new Hit(a, 1.0));

        assertThat(ConvexFusion.fuse(dense, words, 0.8, 10)).extracting(Hit::id).first().isEqualTo(a);
        assertThat(ConvexFusion.fuse(dense, words, 0.2, 10)).extracting(Hit::id).first().isEqualTo(b);
    }

    @Test
    void aMemeMissingFromOneListGetsNothingFromIt() {
        List<Hit> fused = ConvexFusion.fuse(List.of(new Hit(a, 0.9), new Hit(b, 0.5)), List.of(new Hit(c, 4.0)), 0.7, 10);

        // a: 0.7, b: 0, c: 0.3 (the only keyword hit is the best of its list, so it scales to 1)
        assertThat(fused).extracting(Hit::id).containsExactly(a, c, b);
        assertThat(fused.get(1).score()).isCloseTo(0.3, within(1e-9));
    }

    @Test
    void aListOfOneHitScalesToOne() {
        assertThat(ConvexFusion.fuse(List.of(new Hit(a, 0.4)), List.of(), 0.7, 10).get(0).score()).isCloseTo(0.7, within(1e-9));
    }

    @Test
    void whenTheSemanticSearchIsMissingTheKeywordsAnswerAlone() {
        List<Hit> fused = ConvexFusion.fuse(List.of(), List.of(new Hit(a, 3.0), new Hit(b, 1.0)), 0.7, 10);

        assertThat(fused).extracting(Hit::id).containsExactly(a, b);
    }

    @Test
    void keepsEveryMemeOnceAndHonoursTheLimit() {
        List<Hit> fused = ConvexFusion.fuse(List.of(new Hit(a, 0.9), new Hit(b, 0.5)), List.of(new Hit(b, 2.0), new Hit(c, 1.0)), 0.7, 2);

        assertThat(fused).hasSize(2);
        assertThat(fused).extracting(Hit::id).doesNotHaveDuplicates();
    }
}

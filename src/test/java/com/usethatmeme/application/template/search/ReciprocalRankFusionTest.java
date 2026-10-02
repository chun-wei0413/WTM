package com.usethatmeme.application.template.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.usethatmeme.application.template.search.ReciprocalRankFusion.Scored;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReciprocalRankFusionTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void itemRankedWellInBothListsBeatsItemRankedFirstInOnlyOne() {
        // b is second in both lists; a is first in one and absent from the other.
        List<Scored> fused = ReciprocalRankFusion.fuse(List.of(List.of(a, b), List.of(c, b)), 10);

        assertThat(fused).extracting(Scored::id).first().isEqualTo(b);
    }

    @Test
    void keepsEveryItemOnceAndHonoursLimit() {
        List<Scored> fused = ReciprocalRankFusion.fuse(List.of(List.of(a, b), List.of(b, c)), 2);

        assertThat(fused).hasSize(2);
        assertThat(fused).extracting(Scored::id).doesNotHaveDuplicates();
    }

    @Test
    void worksWhenOneRankingIsEmpty() {
        List<Scored> fused = ReciprocalRankFusion.fuse(List.of(List.of(), List.of(a, b)), 10);

        assertThat(fused).extracting(Scored::id).containsExactly(a, b);
    }

    @Test
    void scoresUseTheReciprocalRankFormula() {
        List<Scored> fused = ReciprocalRankFusion.fuse(List.of(List.of(a)), 10, 60);

        assertThat(fused.get(0).score()).isEqualTo(1.0 / 61);
    }
}

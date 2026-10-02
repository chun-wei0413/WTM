package com.wtm.domain.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SlotTest {

    private static Slot slot(int maxChars, int x, int y, int width, int height) {
        return new Slot(1, "preferred thing", maxChars, true, x, y, width, height);
    }

    @Test
    void acceptsCaptionWithinLimit() {
        assertThat(slot(8, 0, 0, 100, 40).accepts("上線再說")).isTrue();
    }

    @Test
    void countsCharactersNotBytes() {
        assertThat(slot(4, 0, 0, 100, 40).accepts("一二三四")).isTrue();
        assertThat(slot(4, 0, 0, 100, 40).accepts("一二三四五")).isFalse();
    }

    @Test
    void rejectsMissingCaption() {
        assertThat(slot(8, 0, 0, 100, 40).accepts(null)).isFalse();
    }

    @Test
    void fitsWithinImageBounds() {
        Slot s = slot(8, 10, 10, 100, 40);
        assertThat(s.fitsWithin(200, 100)).isTrue();
        assertThat(s.fitsWithin(100, 100)).isFalse();
    }

    @Test
    void rejectsInvalidDefinition() {
        assertThatThrownBy(() -> slot(0, 0, 0, 100, 40)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> slot(8, 0, 0, 0, 40)).isInstanceOf(IllegalArgumentException.class);
    }
}

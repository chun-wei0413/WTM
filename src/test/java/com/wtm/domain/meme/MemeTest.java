package com.wtm.domain.meme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wtm.domain.DomainRuleViolation;
import com.wtm.domain.template.Slot;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemeTest {

    private static final UUID OWNER = UUID.randomUUID();

    private static Slot slot(int no, int maxChars, boolean required) {
        return new Slot(no, "role-" + no, maxChars, required, 0, 0, 100, 50);
    }

    private static TemplateRef drake() {
        return new TemplateRef(UUID.randomUUID(), 1, List.of(slot(1, 8, true), slot(2, 8, true)));
    }

    private static Meme composed() {
        return Meme.compose(MemeId.newId(), OWNER, drake(), Map.of(1, "寫測試", 2, "上線再說"));
    }

    @Test
    void composesWhenEveryRequiredSlotHasACaptionThatFits() {
        Meme meme = composed();

        assertThat(meme.captions()).containsEntry(1, "寫測試").containsEntry(2, "上線再說");
        assertThat(meme.status()).isEqualTo(MemeStatus.COMPOSED);
        assertThat(meme.imageKey()).isNull();
    }

    @Test
    void rejectsMissingRequiredCaption() {
        assertThatThrownBy(() -> Meme.compose(MemeId.newId(), OWNER, drake(), Map.of(1, "寫測試")))
                .isInstanceOf(DomainRuleViolation.class);
    }

    @Test
    void blankCaptionCountsAsMissing() {
        assertThatThrownBy(() -> Meme.compose(MemeId.newId(), OWNER, drake(), Map.of(1, "寫測試", 2, "   ")))
                .isInstanceOf(DomainRuleViolation.class);
    }

    @Test
    void rejectsCaptionLongerThanTheSlotAllows() {
        assertThatThrownBy(() -> Meme.compose(MemeId.newId(), OWNER, drake(), Map.of(1, "一二三四五六七八九", 2, "好")))
                .isInstanceOf(DomainRuleViolation.class);
    }

    @Test
    void rejectsCaptionForSlotTheTemplateDoesNotHave() {
        assertThatThrownBy(() -> Meme.compose(MemeId.newId(), OWNER, drake(), Map.of(1, "a", 2, "b", 3, "c")))
                .isInstanceOf(DomainRuleViolation.class);
    }

    @Test
    void optionalSlotMayBeLeftEmpty() {
        var ref = new TemplateRef(UUID.randomUUID(), 1, List.of(slot(1, 8, true), slot(2, 8, false)));

        Meme meme = Meme.compose(MemeId.newId(), OWNER, ref, Map.of(1, "只填一格"));

        assertThat(meme.captions()).containsOnlyKeys(1);
    }

    @Test
    void templateWithoutSlotsYieldsMemeWithoutCaptions() {
        var ref = new TemplateRef(UUID.randomUUID(), 3, List.of());

        Meme meme = Meme.compose(MemeId.newId(), OWNER, ref, Map.of());

        assertThat(meme.captions()).isEmpty();
        assertThat(meme.template().version()).isEqualTo(3);
    }

    @Test
    void imageCanBeAttachedOnlyOnce() {
        Meme meme = composed();
        meme.attachImage("memes/a.png");

        assertThat(meme.imageKey()).isEqualTo("memes/a.png");
        assertThatThrownBy(() -> meme.attachImage("memes/b.png")).isInstanceOf(DomainRuleViolation.class);
    }

    @Test
    void cannotKeepMemeWithoutImage() {
        assertThatThrownBy(composed()::keep).isInstanceOf(DomainRuleViolation.class);
    }

    @Test
    void keepingMarksMemeAsKeptAndIsRepeatable() {
        Meme meme = composed();
        meme.attachImage("memes/a.png");

        meme.keep();
        meme.keep();

        assertThat(meme.status()).isEqualTo(MemeStatus.KEPT);
    }

    @Test
    void remembersOwner() {
        Meme meme = composed();

        assertThat(meme.isOwnedBy(OWNER)).isTrue();
        assertThat(meme.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    void snapshotKeepsOriginalSlotsEvenIfTemplateChangesLater() {
        TemplateRef original = drake();
        Meme meme = Meme.compose(MemeId.newId(), OWNER, original, Map.of(1, "a", 2, "b"));

        // A later template version with different slots does not touch the stored reference.
        var revised = new TemplateRef(original.templateId(), 2, List.of(slot(1, 4, true)));

        assertThat(meme.template().version()).isEqualTo(1);
        assertThat(meme.template().slots()).hasSize(2);
        assertThat(revised.slots()).hasSize(1);
    }
}

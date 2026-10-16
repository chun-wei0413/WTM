package com.wtm.curation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wtm.shared.DomainRuleViolation;
import com.wtm.shared.TemplateId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageInfoTest {

    @Test
    void aPictureKnownByItsKeyAndSizeHasNoFingerprint() {
        ImageInfo info = ImageInfo.of("library/a.png", 600, 400);

        assertThat(info.key()).isEqualTo("library/a.png");
        assertThat(info.width()).isEqualTo(600);
        assertThat(info.height()).isEqualTo(400);
        assertThat(info.sha256()).isNull();
        assertThat(info.perceptualHash()).isNull();
    }

    @Test
    void aPictureNeedsAKeyAndAPositiveSize() {
        assertThatThrownBy(() -> ImageInfo.of(" ", 600, 400)).isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> ImageInfo.of(null, 600, 400)).isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> ImageInfo.of("a.png", 0, 400)).isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> ImageInfo.of("a.png", 600, -1)).isInstanceOf(DomainRuleViolation.class);
    }

    @Test
    void aTemplateKeepsItsPictureWithTheValuesThatTellItApart() {
        ImageInfo info = new ImageInfo("library/a.png", 600, 400, "abc123", 42L);

        MemeTemplate template = MemeTemplate.draft(TemplateId.newId(), "貓", info);

        assertThat(template.image()).isEqualTo(info);
        assertThat(template.imageKey()).isEqualTo("library/a.png");
        assertThat(template.imageWidth()).isEqualTo(600);
        assertThat(template.imageHeight()).isEqualTo(400);
    }

    @Test
    void aTemplateRestoredFromStorageHasThePictureItWasKeptWith() {
        ImageInfo info = new ImageInfo("library/a.png", 600, 400, "abc123", 42L);

        MemeTemplate template = MemeTemplate.restore(TemplateId.newId(), "貓", info, TemplateStatus.DRAFT, 1,
                com.wtm.shared.MemeProfile.empty(), List.of(), List.of());

        assertThat(template.image().sha256()).isEqualTo("abc123");
        assertThat(template.image().perceptualHash()).isEqualTo(42L);
    }
}

package com.wtm.domain.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wtm.domain.DomainRuleViolation;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemeTemplateTest {

    private static final MemeProfile COMPLETE_PROFILE = new MemeProfile(
            "Rejecting one thing in favor of another",
            List.of("skip writing tests, ship it"), List.of("smug"), List.of("Drake"));

    private static MemeTemplate draft() {
        return MemeTemplate.draft(TemplateId.newId(), "Drake", "templates/drake.png", 600, 600);
    }

    private static Slot slot(int slotNo, int maxChars) {
        return new Slot(slotNo, "role-" + slotNo, maxChars, true, 300, (slotNo - 1) * 300, 300, 300);
    }

    private static MemeTemplate approvedWithTwoSlots() {
        MemeTemplate template = draft();
        template.defineSlot(slot(1, 10));
        template.defineSlot(slot(2, 10));
        template.reviseProfile(COMPLETE_PROFILE);
        template.approve();
        return template;
    }

    @Test
    void newDraftStartsAtVersionOneWithoutSlots() {
        MemeTemplate template = draft();

        assertThat(template.status()).isEqualTo(TemplateStatus.DRAFT);
        assertThat(template.version()).isEqualTo(1);
        assertThat(template.slots()).isEmpty();
        assertThat(template.isUsable()).isFalse();
    }

    @Test
    void cannotApproveWithoutCompleteProfile() {
        MemeTemplate template = draft();

        assertThatThrownBy(template::approve).isInstanceOf(DomainRuleViolation.class);
    }

    @Test
    void canApproveTemplateWithoutAnySlot() {
        MemeTemplate template = draft();
        template.reviseProfile(COMPLETE_PROFILE);

        template.approve();

        assertThat(template.isUsable()).isTrue();
    }

    @Test
    void cannotApproveTwice() {
        MemeTemplate template = approvedWithTwoSlots();

        assertThatThrownBy(template::approve).isInstanceOf(DomainRuleViolation.class);
    }

    @Test
    void rejectsSlotOutsideImage() {
        MemeTemplate template = draft();
        Slot outside = new Slot(1, "role", 10, true, 500, 500, 200, 200);

        assertThatThrownBy(() -> template.defineSlot(outside)).isInstanceOf(DomainRuleViolation.class);
    }

    @Test
    void rejectsDuplicateSlotNumber() {
        MemeTemplate template = draft();
        template.defineSlot(slot(1, 10));

        assertThatThrownBy(() -> template.defineSlot(slot(1, 12))).isInstanceOf(DomainRuleViolation.class);
    }

    @Test
    void changingSlotsOfDraftDoesNotBumpVersion() {
        MemeTemplate template = draft();
        template.defineSlot(slot(1, 10));
        template.redefineSlot(slot(1, 8));

        assertThat(template.version()).isEqualTo(1);
    }

    @Test
    void changingSlotsOfApprovedTemplateBumpsVersion() {
        MemeTemplate template = approvedWithTwoSlots();

        template.redefineSlot(slot(1, 8));

        assertThat(template.version()).isEqualTo(2);
    }

    @Test
    void addingAndRemovingSlotsOnApprovedTemplateEachBumpVersion() {
        MemeTemplate template = approvedWithTwoSlots();

        template.defineSlot(new Slot(3, "role-3", 10, true, 0, 0, 100, 100));
        template.removeSlot(3);

        assertThat(template.version()).isEqualTo(3);
    }

    @Test
    void approvedTemplateMustKeepCompleteProfile() {
        MemeTemplate template = approvedWithTwoSlots();

        assertThatThrownBy(() -> template.reviseProfile(MemeProfile.empty()))
                .isInstanceOf(DomainRuleViolation.class);
    }

    @Test
    void revisingProfileOfApprovedTemplateDoesNotBumpVersion() {
        MemeTemplate template = approvedWithTwoSlots();

        template.reviseProfile(new MemeProfile("New meaning", List.of("new usage"), List.of(), List.of()));

        assertThat(template.version()).isEqualTo(1);
        assertThat(template.profile().meaning()).isEqualTo("New meaning");
    }

    @Test
    void retiredTemplateIsNotUsableAndCannotChange() {
        MemeTemplate template = approvedWithTwoSlots();

        template.retire();

        assertThat(template.isUsable()).isFalse();
        assertThatThrownBy(() -> template.defineSlot(slot(3, 10))).isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> template.reviseProfile(COMPLETE_PROFILE)).isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(template::retire).isInstanceOf(DomainRuleViolation.class);
    }

    @Test
    void restoreRebuildsStoredState() {
        MemeTemplate restored = MemeTemplate.restore(TemplateId.newId(), "Drake", "k", 600, 600,
                TemplateStatus.APPROVED, 3, COMPLETE_PROFILE, List.of(slot(1, 10)));

        assertThat(restored.version()).isEqualTo(3);
        assertThat(restored.status()).isEqualTo(TemplateStatus.APPROVED);
        assertThat(restored.slots()).hasSize(1);
    }
}

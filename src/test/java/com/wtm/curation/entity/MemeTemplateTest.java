package com.wtm.curation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wtm.shared.DomainRuleViolation;
import com.wtm.shared.MemeProfile;
import com.wtm.shared.Slot;
import com.wtm.shared.TemplateId;
import java.time.Instant;
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

    // -- the description proposed by someone else, and taking it back ---------------------

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    private static final MemeProfile PROPOSED = new MemeProfile("Another meaning", List.of("another use"),
            List.of("calm"), List.of("Drake"));

    @Test
    void anAdoptedProposalReplacesTheDescriptionAndIsRemembered() {
        MemeTemplate template = approvedWithTwoSlots();

        template.adoptProfile(PROPOSED, ProfileChange.Source.AUTO, NOW);

        assertThat(template.profile()).isEqualTo(PROPOSED);
        assertThat(template.profileChanges()).singleElement().satisfies(change -> {
            assertThat(change.before()).isEqualTo(COMPLETE_PROFILE);
            assertThat(change.after()).isEqualTo(PROPOSED);
            assertThat(change.source()).isEqualTo(ProfileChange.Source.AUTO);
            assertThat(change.createdAt()).isEqualTo(NOW);
            assertThat(change.isUndone()).isFalse();
        });
        assertThat(template.status()).isEqualTo(TemplateStatus.APPROVED);
    }

    @Test
    void whatTheRulesChangedCanBeTakenBack() {
        MemeTemplate template = approvedWithTwoSlots();
        template.adoptProfile(PROPOSED, ProfileChange.Source.AUTO, NOW);
        assertThat(template.canRestoreProfile()).isTrue();

        boolean restored = template.restoreProfile(NOW.plusSeconds(60));

        assertThat(restored).isTrue();
        assertThat(template.profile()).isEqualTo(COMPLETE_PROFILE);
        assertThat(template.profileChanges().get(0).undoneAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(template.canRestoreProfile()).as("only once").isFalse();
        assertThat(template.restoreProfile(NOW.plusSeconds(120))).isFalse();
    }

    @Test
    void whatAnAdministratorAdoptedIsRememberedButNotTakenBackByTheRulesUndo() {
        MemeTemplate template = approvedWithTwoSlots();
        template.adoptProfile(PROPOSED, ProfileChange.Source.ADMIN, NOW);

        assertThat(template.canRestoreProfile()).isFalse();
        assertThat(template.restoreProfile(NOW.plusSeconds(1))).isFalse();
        assertThat(template.profile()).isEqualTo(PROPOSED);
    }

    @Test
    void aChangeIsNotTakenBackOverALaterEdit() {
        MemeTemplate template = approvedWithTwoSlots();
        template.adoptProfile(PROPOSED, ProfileChange.Source.AUTO, NOW);
        template.reviseProfile(new MemeProfile("Edited by hand", List.of("x"), List.of(), List.of()));

        assertThatThrownBy(() -> template.restoreProfile(NOW.plusSeconds(60)))
                .isInstanceOf(DomainRuleViolation.class);
        assertThat(template.profileChanges().get(0).isUndone()).isFalse();
    }

    @Test
    void onlyTheLatestChangeTheRulesMadeIsTakenBack() {
        MemeTemplate template = approvedWithTwoSlots();
        template.adoptProfile(PROPOSED, ProfileChange.Source.AUTO, NOW);
        MemeProfile later = new MemeProfile("Third meaning", List.of("third use"), List.of(), List.of("Drake"));
        template.adoptProfile(later, ProfileChange.Source.AUTO, NOW.plusSeconds(10));

        assertThat(template.restoreProfile(NOW.plusSeconds(20))).isTrue();

        assertThat(template.profile()).isEqualTo(PROPOSED);
        assertThat(template.canRestoreProfile()).as("the earlier one can still be taken back").isTrue();
    }

    @Test
    void aRetiredTemplateCannotHaveItsDescriptionChangedOrPutBack() {
        MemeTemplate template = approvedWithTwoSlots();
        template.adoptProfile(PROPOSED, ProfileChange.Source.AUTO, NOW);
        template.retire();

        assertThatThrownBy(() -> template.restoreProfile(NOW)).isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> template.adoptProfile(COMPLETE_PROFILE, ProfileChange.Source.AUTO, NOW))
                .isInstanceOf(DomainRuleViolation.class);
    }

    @Test
    void theChangesThatWereKeptComeBackWithTheTemplate() {
        var change = new ProfileChange(java.util.UUID.randomUUID(), COMPLETE_PROFILE, PROPOSED,
                ProfileChange.Source.AUTO, NOW, null);

        MemeTemplate template = MemeTemplate.restore(TemplateId.newId(), "Drake", "templates/drake.png", 600, 600,
                TemplateStatus.APPROVED, 1, PROPOSED, List.of(), List.of(change));

        assertThat(template.canRestoreProfile()).isTrue();
        assertThat(template.restoreProfile(NOW.plusSeconds(1))).isTrue();
        assertThat(template.profile()).isEqualTo(COMPLETE_PROFILE);
    }
}

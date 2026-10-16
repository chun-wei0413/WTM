package com.wtm.curation.usecase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtm.curation.entity.MemeTemplate;
import com.wtm.curation.entity.ProfileChange;
import com.wtm.curation.entity.TemplateStatus;
import com.wtm.curation.usecase.port.in.command.ReviseProfileFromProposalUseCase.Input;
import com.wtm.curation.usecase.port.out.template.TemplateRepository;
import com.wtm.shared.DomainRuleViolation;
import com.wtm.shared.MemeProfile;
import com.wtm.shared.TemplateId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileProposalServicesTest {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

    private final TemplateRepository templates = mock(TemplateRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final UUID id = UUID.randomUUID();

    private static final MemeProfile CURRENT = new MemeProfile("舊的意思", List.of("舊情境"), List.of("無奈"),
            List.of("別名"), "舊文字", List.of("舊標籤"));
    private static final MemeProfile PROPOSED = new MemeProfile("新的意思", List.of("新情境"), List.of("得意"),
            List.of("別名"), "新文字", List.of("新標籤"));

    private MemeTemplate approved() {
        return MemeTemplate.restore(new TemplateId(id), "貓", "library/a.png", 600, 400,
                TemplateStatus.APPROVED, 1, CURRENT, List.of());
    }

    private Input proposal(boolean automatic) {
        return new Input(id, "新的意思", List.of("新情境"), List.of("得意"), "新文字", List.of("新標籤"), automatic);
    }

    @Test
    void theProposalReplacesTheDescriptionButKeepsTheOtherNamesAndIsSaved() {
        MemeTemplate template = approved();
        when(templates.getForUpdate(new TemplateId(id))).thenReturn(template);

        new ReviseProfileFromProposalService(templates, clock).handle(proposal(false));

        assertThat(template.profile()).isEqualTo(PROPOSED);
        assertThat(template.profileChanges()).singleElement()
                .satisfies(c -> assertThat(c.source()).isEqualTo(ProfileChange.Source.ADMIN));
        verify(templates).save(template);
    }

    @Test
    void aChangeByTheRulesIsRememberedAsSuch() {
        MemeTemplate template = approved();
        when(templates.getForUpdate(new TemplateId(id))).thenReturn(template);

        new ReviseProfileFromProposalService(templates, clock).handle(proposal(true));

        assertThat(template.profileChanges()).singleElement().satisfies(c -> {
            assertThat(c.source()).isEqualTo(ProfileChange.Source.AUTO);
            assertThat(c.createdAt()).isEqualTo(NOW);
        });
    }

    @Test
    void aProposalWithoutAMeaningIsNotWritten() {
        MemeTemplate template = approved();
        when(templates.getForUpdate(any())).thenReturn(template);
        var empty = new Input(id, "", List.of(), List.of(), "", List.of(), false);

        assertThatThrownBy(() -> new ReviseProfileFromProposalService(templates, clock).handle(empty))
                .isInstanceOf(DomainRuleViolation.class);
        verify(templates, never()).save(any());
        assertThat(template.profile()).isEqualTo(CURRENT);
    }

    @Test
    void aChangeByTheRulesIsPutBackAndSaved() {
        MemeTemplate template = approved();
        template.adoptProfile(PROPOSED, ProfileChange.Source.AUTO, NOW.minusSeconds(60));
        when(templates.findByIdForUpdate(new TemplateId(id))).thenReturn(Optional.of(template));

        boolean restored = new RestoreProfileService(templates, clock).handle(id);

        assertThat(restored).isTrue();
        assertThat(template.profile()).isEqualTo(CURRENT);
        verify(templates).save(template);
    }

    @Test
    void aChangeIsNotPutBackOverALaterEdit() {
        MemeTemplate template = approved();
        template.adoptProfile(PROPOSED, ProfileChange.Source.AUTO, NOW.minusSeconds(60));
        template.reviseProfile(new MemeProfile("管理員後來改的", List.of("x"), List.of(), List.of()));
        when(templates.findByIdForUpdate(new TemplateId(id))).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> new RestoreProfileService(templates, clock).handle(id))
                .isInstanceOf(DomainRuleViolation.class);
        verify(templates, never()).save(any());
    }

    @Test
    void thereIsNothingToPutBackWhenTheRulesChangedNothingOrTheMemeIsGone() {
        MemeTemplate template = approved();
        when(templates.findByIdForUpdate(new TemplateId(id))).thenReturn(Optional.of(template));
        var service = new RestoreProfileService(templates, clock);

        assertThat(service.handle(id)).isFalse();
        verify(templates, never()).save(any());

        UUID gone = UUID.randomUUID();
        when(templates.findByIdForUpdate(new TemplateId(gone))).thenReturn(Optional.empty());
        assertThat(service.handle(gone)).isFalse();
    }

    @Test
    void aChangeThatIsNotTakenBackYetCanBeAndOnceItIsNotAnymore() {
        MemeTemplate template = approved();
        template.adoptProfile(PROPOSED, ProfileChange.Source.AUTO, NOW.minusSeconds(60));
        when(templates.findById(new TemplateId(id))).thenReturn(Optional.of(template));
        var service = new RestoreProfileService(templates, clock);

        assertThat(service.canRestore(id)).isTrue();
        template.restoreProfile(NOW);
        assertThat(service.canRestore(id)).isFalse();
        assertThat(service.canRestore(UUID.randomUUID())).isFalse();
    }
}

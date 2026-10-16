package com.wtm.curation.usecase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtm.curation.entity.MemeTemplate;
import com.wtm.curation.entity.TemplateStatus;
import com.wtm.curation.usecase.port.in.command.ReviseProfileFromProposalUseCase.Input;
import com.wtm.curation.usecase.port.out.template.ProfileHistoryPort;
import com.wtm.curation.usecase.port.out.template.ProfileHistoryPort.Change;
import com.wtm.curation.usecase.port.out.template.ProfileHistoryPort.Source;
import com.wtm.curation.usecase.port.out.template.TemplateRepository;
import com.wtm.shared.DomainRuleViolation;
import com.wtm.shared.MemeProfile;
import com.wtm.shared.TemplateId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileProposalServicesTest {

    private final TemplateRepository templates = mock(TemplateRepository.class);
    private final ProfileHistoryPort history = mock(ProfileHistoryPort.class);
    private final UUID id = UUID.randomUUID();

    private static final MemeProfile CURRENT = new MemeProfile("舊的意思", List.of("舊情境"), List.of("無奈"),
            List.of("別名"), "舊文字", List.of("舊標籤"));
    private static final MemeProfile PROPOSED = new MemeProfile("新的意思", List.of("新情境"), List.of("得意"),
            List.of("別名"), "新文字", List.of("新標籤"));

    private MemeTemplate approved() {
        return MemeTemplate.restore(new TemplateId(id), "貓", "library/a.png", 600, 400,
                TemplateStatus.APPROVED, 1, CURRENT, List.of());
    }

    private Input proposalFor(boolean automatic) {
        return new Input(id, "新的意思", List.of("新情境"), List.of("得意"), "新文字", List.of("新標籤"), automatic);
    }

    // -- writing the proposal ------------------------------------------------------

    @Test
    void theProposalReplacesTheDescriptionButKeepsTheOtherNames() {
        MemeTemplate template = approved();
        when(templates.getForUpdate(new TemplateId(id))).thenReturn(template);

        new ReviseProfileFromProposalService(templates, history).handle(proposalFor(false));

        assertThat(template.profile()).isEqualTo(PROPOSED);
        assertThat(template.profile().aliases()).containsExactly("別名");
        assertThat(template.status()).isEqualTo(TemplateStatus.APPROVED);
        verify(templates).save(template);
        verify(history).record(eq(id), eq(CURRENT), eq(PROPOSED), eq(Source.ADMIN));
    }

    @Test
    void aChangeByTheRulesIsRecordedAsSuchSoItCanBeTakenBack() {
        when(templates.getForUpdate(new TemplateId(id))).thenReturn(approved());

        new ReviseProfileFromProposalService(templates, history).handle(proposalFor(true));

        verify(history).record(eq(id), eq(CURRENT), any(), eq(Source.AUTO));
    }

    @Test
    void aProposalWithoutAMeaningIsNotWritten() {
        when(templates.getForUpdate(any())).thenReturn(approved());
        var empty = new Input(id, "", List.of(), List.of(), "", List.of(), false);

        assertThatThrownBy(() -> new ReviseProfileFromProposalService(templates, history).handle(empty))
                .isInstanceOf(DomainRuleViolation.class);
        verify(templates, never()).save(any());
        verify(history, never()).record(any(), any(), any(), any());
    }

    // -- taking it back -------------------------------------------------------------

    @Test
    void anAutomaticChangeIsPutBackToTheEarlierDescription() {
        MemeTemplate template = approved();
        template.reviseProfile(PROPOSED);
        when(templates.getForUpdate(new TemplateId(id))).thenReturn(template);
        var change = new Change(UUID.randomUUID(), CURRENT, PROPOSED);
        when(history.latestAutomaticChange(id)).thenReturn(Optional.of(change));

        boolean restored = new RestoreProfileService(templates, history).handle(id);

        assertThat(restored).isTrue();
        assertThat(template.profile()).isEqualTo(CURRENT);
        verify(templates).save(template);
        verify(history).markUndone(change.id());
    }

    @Test
    void aChangeIsNotPutBackOverALaterEdit() {
        MemeTemplate template = approved();
        template.reviseProfile(new MemeProfile("管理員後來改的", List.of("x"), List.of(), List.of()));
        when(templates.getForUpdate(new TemplateId(id))).thenReturn(template);
        when(history.latestAutomaticChange(id))
                .thenReturn(Optional.of(new Change(UUID.randomUUID(), CURRENT, PROPOSED)));

        assertThatThrownBy(() -> new RestoreProfileService(templates, history).handle(id))
                .isInstanceOf(DomainRuleViolation.class);
        verify(history, never()).markUndone(any());
    }

    @Test
    void thereIsNothingToPutBackWhenTheRulesChangedNothing() {
        when(history.latestAutomaticChange(id)).thenReturn(Optional.empty());

        var service = new RestoreProfileService(templates, history);

        assertThat(service.handle(id)).isFalse();
        assertThat(service.canRestore(id)).isFalse();
        verify(templates, never()).save(any());
    }

    @Test
    void aChangeThatIsNotTakenBackYetCanBe() {
        when(history.latestAutomaticChange(id))
                .thenReturn(Optional.of(new Change(UUID.randomUUID(), CURRENT, PROPOSED)));

        assertThat(new RestoreProfileService(templates, history).canRestore(id)).isTrue();
    }
}

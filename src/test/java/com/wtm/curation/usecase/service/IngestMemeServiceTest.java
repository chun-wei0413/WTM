package com.wtm.curation.usecase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtm.curation.entity.MemeTemplate;
import com.wtm.curation.entity.Origin;
import com.wtm.curation.entity.TemplateStatus;
import com.wtm.curation.usecase.port.in.DuplicateImageException;
import com.wtm.curation.usecase.port.in.UnsupportedImageException;
import com.wtm.curation.usecase.port.in.command.IngestMemeUseCase;
import com.wtm.curation.usecase.port.in.command.IngestOutcome;
import com.wtm.curation.usecase.port.in.command.IngestOutcome.Status;
import com.wtm.curation.usecase.port.out.image.ImageFingerprintPort;
import com.wtm.curation.usecase.port.out.image.ImageFingerprintPort.Fingerprint;
import com.wtm.curation.usecase.port.out.image.ImageInspectorPort;
import com.wtm.curation.usecase.port.out.image.ImageInspectorPort.ImageInfo;
import com.wtm.curation.usecase.port.out.library.LibraryPort;
import com.wtm.shared.port.ObjectStoragePort;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IngestMemeServiceTest {

    private final ImageInspectorPort inspector = mock(ImageInspectorPort.class);
    private final ImageFingerprintPort fingerprints = mock(ImageFingerprintPort.class);
    private final LibraryPort library = mock(LibraryPort.class);
    private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
    private final IngestMemeService handler = new IngestMemeService(inspector, fingerprints, library, storage);

    private final byte[] image = {1, 2, 3};
    private final Fingerprint fingerprint = new Fingerprint("abc123", 42L);
    private final Origin origin = new Origin("PTT", "https://i.example/a.jpg", "https://page", "someone", null);

    @BeforeEach
    void setUp() {
        when(inspector.inspect(image)).thenReturn(new ImageInfo(640, 480, "jpg", "image/jpeg"));
        when(fingerprints.of(image)).thenReturn(fingerprint);
        when(library.findDuplicate(anyString(), anyLong(), anyInt())).thenReturn(Optional.empty());
    }

    @Test
    void addsAPictureAsADraftWithItsOriginAndFingerprint() {
        IngestOutcome outcome = handler.handle(new IngestMemeUseCase.Input(image, "Cat staring.jpg", origin));

        assertThat(outcome.status()).isEqualTo(Status.IMPORTED);
        ArgumentCaptor<MemeTemplate> added = ArgumentCaptor.forClass(MemeTemplate.class);
        verify(library).add(added.capture(), eq(origin), eq(fingerprint));
        MemeTemplate template = added.getValue();
        assertThat(template.id().value()).isEqualTo(outcome.templateId());
        assertThat(template.name()).isEqualTo("Cat staring");
        assertThat(template.status()).isEqualTo(TemplateStatus.DRAFT);
        assertThat(template.imageKey()).isEqualTo("library/" + outcome.templateId() + ".jpg");
        assertThat(template.imageWidth()).isEqualTo(640);
        verify(storage).put(eq(template.imageKey()), eq(image), eq("image/jpeg"));
    }

    @Test
    void aPictureTheLibraryAlreadyHasIsNotAddedAgain() {
        UUID existing = UUID.randomUUID();
        when(library.findDuplicate(eq("abc123"), eq(42L), anyInt())).thenReturn(Optional.of(existing));

        IngestOutcome outcome = handler.handle(new IngestMemeUseCase.Input(image, "again.jpg", origin));

        assertThat(outcome.status()).isEqualTo(Status.DUPLICATE);
        assertThat(outcome.templateId()).isEqualTo(existing);
        verify(storage, never()).put(anyString(), any(), anyString());
        verify(library, never()).add(any(), any(), any());
    }

    @Test
    void twoIdenticalPicturesAddedAtOnceKeepOnlyOneAndCleanUp() {
        UUID winner = UUID.randomUUID();
        when(library.findDuplicate(anyString(), anyLong(), anyInt()))
                .thenReturn(Optional.empty())          // the check before adding
                .thenReturn(Optional.of(winner));      // the lookup after losing the race
        doThrow(new DuplicateImageException()).when(library).add(any(), any(), any());

        IngestOutcome outcome = handler.handle(new IngestMemeUseCase.Input(image, "race.jpg", origin));

        assertThat(outcome.status()).isEqualTo(Status.DUPLICATE);
        assertThat(outcome.templateId()).isEqualTo(winner);
        verify(storage).delete(startsWith("library/"));
    }

    @Test
    void anUnreadablePictureIsRejectedWithoutStoringAnything() {
        when(inspector.inspect(image)).thenThrow(new UnsupportedImageException("Unrecognized image format"));

        IngestOutcome outcome = handler.handle(new IngestMemeUseCase.Input(image, "x.jpg", origin));

        assertThat(outcome.status()).isEqualTo(Status.REJECTED);
        assertThat(outcome.reason()).contains("Unrecognized");
        verify(storage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void tinyPicturesAreRejected() {
        when(inspector.inspect(image)).thenReturn(new ImageInfo(64, 64, "png", "image/png"));

        IngestOutcome outcome = handler.handle(new IngestMemeUseCase.Input(image, "icon.png", origin));

        assertThat(outcome.status()).isEqualTo(Status.REJECTED);
        assertThat(outcome.reason()).contains("too small");
    }

    @Test
    void removesTheStoredImageWhenTheDatabaseWriteFails() {
        doThrow(new IllegalStateException("db down")).when(library).add(any(), any(), any());

        assertThatThrownBy(() -> handler.handle(new IngestMemeUseCase.Input(image, "x.jpg", origin))).isInstanceOf(IllegalStateException.class);

        verify(storage).delete(startsWith("library/"));
    }

    @Test
    void turnsFileNamesAndTitlesIntoTidyNames() {
        assertThat(IngestMemeService.nameFor("distracted_boyfriend-1920.JPG")).isEqualTo("distracted boyfriend 1920");
        assertThat(IngestMemeService.nameFor("  Drake   Hotline Bling  ")).isEqualTo("Drake Hotline Bling");
        assertThat(IngestMemeService.nameFor("老闆又改需求.png")).isEqualTo("老闆又改需求");
    }

    @Test
    void usesAPlaceholderWhenThereIsNoUsefulName() {
        String placeholder = IngestMemeService.PLACEHOLDER_NAME;

        assertThat(IngestMemeService.nameFor(null)).isEqualTo(placeholder);
        assertThat(IngestMemeService.nameFor("   ")).isEqualTo(placeholder);
        assertThat(IngestMemeService.nameFor(".jpg")).isEqualTo(placeholder);
        assertThat(IngestMemeService.nameFor("3f2a9c1b7d4e8f60a1b2c3d4e5f60718.jpg")).isEqualTo(placeholder);
    }

    @Test
    void longNamesAreCut() {
        assertThat(IngestMemeService.nameFor("字".repeat(200)).codePointCount(0, 60)).isEqualTo(60);
    }

    private static String startsWith(String prefix) {
        return org.mockito.ArgumentMatchers.startsWith(prefix);
    }
}

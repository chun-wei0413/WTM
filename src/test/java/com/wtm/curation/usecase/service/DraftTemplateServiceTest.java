package com.wtm.curation.usecase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtm.curation.entity.MemeTemplate;
import com.wtm.curation.usecase.port.in.UnsupportedImageException;
import com.wtm.curation.usecase.port.in.command.DraftTemplateUseCase;
import com.wtm.curation.usecase.port.out.image.ImageInspectorPort;
import com.wtm.curation.usecase.port.out.image.ImageInspectorPort.InspectedImage;
import com.wtm.curation.usecase.port.out.template.TemplateRepository;
import com.wtm.shared.DomainRuleViolation;
import com.wtm.shared.TemplateId;
import com.wtm.shared.port.ObjectStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DraftTemplateServiceTest {

    private final ImageInspectorPort inspector = mock(ImageInspectorPort.class);
    private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
    private final TemplateRepository templates = mock(TemplateRepository.class);
    private DraftTemplateService handler;
    private final byte[] image = {1, 2, 3};

    @BeforeEach
    void setUp() {
        handler = new DraftTemplateService(inspector, storage, templates);
        when(inspector.inspect(image)).thenReturn(new InspectedImage(600, 400, "png", "image/png"));
    }

    @Test
    void storesImageThenSavesDraft() {
        TemplateId id = handler.handle(new DraftTemplateUseCase.Input("Drake", image));

        ArgumentCaptor<MemeTemplate> saved = ArgumentCaptor.forClass(MemeTemplate.class);
        verify(templates).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(id);
        assertThat(saved.getValue().imageKey()).isEqualTo("templates/" + id.value() + ".png");
        assertThat(saved.getValue().imageWidth()).isEqualTo(600);
        verify(storage).put(eq(saved.getValue().imageKey()), eq(image), eq("image/png"));
    }

    @Test
    void removesUploadedImageWhenSavingFails() {
        doThrow(new IllegalStateException("db down")).when(templates).save(any());

        assertThatThrownBy(() -> handler.handle(new DraftTemplateUseCase.Input("Drake", image))).isInstanceOf(IllegalStateException.class);

        verify(storage).delete(anyString());
    }

    @Test
    void doesNotUploadWhenNameIsInvalid() {
        assertThatThrownBy(() -> handler.handle(new DraftTemplateUseCase.Input("  ", image))).isInstanceOf(DomainRuleViolation.class);

        verify(storage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void doesNotUploadWhenImageIsRejected() {
        byte[] bad = {9};
        when(inspector.inspect(bad)).thenThrow(new UnsupportedImageException("nope"));

        assertThatThrownBy(() -> handler.handle(new DraftTemplateUseCase.Input("Drake", bad))).isInstanceOf(UnsupportedImageException.class);

        verify(storage, never()).put(anyString(), any(), anyString());
    }
}

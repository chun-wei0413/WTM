package com.wtm.curation.usecase.port.in.command;

import com.wtm.shared.port.ImageTags;
import java.util.UUID;

public interface ApplyTagsUseCase {

    void handle(Input input);

    record Input(UUID templateId, ImageTags tags) {
    }
}

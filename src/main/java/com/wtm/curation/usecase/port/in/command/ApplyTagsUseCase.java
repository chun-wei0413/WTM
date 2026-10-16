package com.wtm.curation.usecase.port.in.command;

import com.wtm.curation.usecase.port.out.vision.ImageTags;
import java.util.UUID;

public interface ApplyTagsUseCase {

    void handle(UUID templateId, ImageTags tags);
}

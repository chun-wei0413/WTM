package com.wtm.curation.usecase.port.in.command;

import java.util.UUID;

public interface TagTemplateUseCase {

    void handle(UUID templateId);
}

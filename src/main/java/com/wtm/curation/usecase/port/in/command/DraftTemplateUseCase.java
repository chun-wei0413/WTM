package com.wtm.curation.usecase.port.in.command;

import com.wtm.shared.TemplateId;

public interface DraftTemplateUseCase {

    TemplateId handle(Input input);

    record Input(String name, byte[] image) {
    }
}

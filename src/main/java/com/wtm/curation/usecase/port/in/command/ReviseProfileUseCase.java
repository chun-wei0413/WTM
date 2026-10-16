package com.wtm.curation.usecase.port.in.command;

import com.wtm.shared.MemeProfile;
import com.wtm.shared.TemplateId;

public interface ReviseProfileUseCase {

    void handle(Input input);

    record Input(TemplateId id, MemeProfile profile) {
    }
}

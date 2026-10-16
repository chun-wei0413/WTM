package com.wtm.curation.usecase.port.in.command;

import com.wtm.curation.entity.MemeProfile;
import com.wtm.shared.TemplateId;

public interface ReviseProfileUseCase {

    void handle(TemplateId id, MemeProfile profile);
}

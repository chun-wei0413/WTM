package com.wtm.curation.usecase.port.in.command;

import com.wtm.shared.TemplateId;

public interface ApproveTemplateUseCase {

    void handle(TemplateId id);
}

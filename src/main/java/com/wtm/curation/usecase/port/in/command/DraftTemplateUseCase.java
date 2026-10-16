package com.wtm.curation.usecase.port.in.command;

import com.wtm.shared.TemplateId;

public interface DraftTemplateUseCase {

    TemplateId handle(String name, byte[] image);
}

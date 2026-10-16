package com.wtm.curation.usecase.port.in.command;

import com.wtm.shared.TemplateId;

public interface RemoveSlotUseCase {

    void handle(TemplateId id, int slotNo);
}

package com.wtm.curation.usecase.port.in.command;

import com.wtm.shared.TemplateId;

public interface RemoveSlotUseCase {

    void handle(Input input);

    record Input(TemplateId id, int slotNo) {
    }
}

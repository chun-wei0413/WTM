package com.wtm.curation.usecase.port.in.command;

import com.wtm.shared.Slot;
import com.wtm.shared.TemplateId;

public interface RedefineSlotUseCase {

    void handle(TemplateId id, Slot slot);
}

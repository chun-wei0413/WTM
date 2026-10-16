package com.wtm.curation.adapter.in.web;

import com.wtm.curation.usecase.port.in.command.RemoveSlotUseCase;
import com.wtm.shared.TemplateId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RemoveSlotController {

    private final RemoveSlotUseCase removeSlot;

    RemoveSlotController(RemoveSlotUseCase removeSlot) {
        this.removeSlot = removeSlot;
    }

    @DeleteMapping("/api/admin/templates/{id}/slots/{slotNo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeSlot(@PathVariable UUID id, @PathVariable int slotNo) {
        removeSlot.handle(new RemoveSlotUseCase.Input(new TemplateId(id), slotNo));
    }
}

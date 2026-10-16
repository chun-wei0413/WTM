package com.wtm.curation.adapter.in.web;

import com.wtm.curation.usecase.port.in.command.RedefineSlotUseCase;
import com.wtm.shared.TemplateId;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RedefineSlotController {

    private final RedefineSlotUseCase redefineSlot;

    RedefineSlotController(RedefineSlotUseCase redefineSlot) {
        this.redefineSlot = redefineSlot;
    }

    @PutMapping("/api/admin/templates/{id}/slots/{slotNo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void redefineSlot(@PathVariable UUID id, @PathVariable int slotNo,
                      @Valid @RequestBody SlotRequest request) {
        redefineSlot.handle(new RedefineSlotUseCase.Input(new TemplateId(id), request.toSlot(slotNo)));
    }
}

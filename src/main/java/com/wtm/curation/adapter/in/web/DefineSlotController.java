package com.wtm.curation.adapter.in.web;

import com.wtm.curation.usecase.port.in.command.DefineSlotUseCase;
import com.wtm.shared.TemplateId;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DefineSlotController {

    private final DefineSlotUseCase defineSlot;

    DefineSlotController(DefineSlotUseCase defineSlot) {
        this.defineSlot = defineSlot;
    }

    @PostMapping("/api/admin/templates/{id}/slots")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void defineSlot(@PathVariable UUID id, @Valid @RequestBody SlotRequest request) {
        defineSlot.handle(new DefineSlotUseCase.Input(new TemplateId(id), request.toSlot(request.slotNo())));
    }
}

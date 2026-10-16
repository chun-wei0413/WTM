package com.wtm.curation.adapter.in.web;

import com.wtm.curation.usecase.port.in.command.RetireTemplateUseCase;
import com.wtm.shared.TemplateId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RetireTemplateController {

    private final RetireTemplateUseCase retire;

    RetireTemplateController(RetireTemplateUseCase retire) {
        this.retire = retire;
    }

    @PostMapping("/api/admin/templates/{id}/retire")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void retire(@PathVariable UUID id) {
        retire.handle(new TemplateId(id));
    }
}

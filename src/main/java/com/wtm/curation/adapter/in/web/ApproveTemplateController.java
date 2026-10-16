package com.wtm.curation.adapter.in.web;

import com.wtm.curation.usecase.port.in.command.ApproveTemplateUseCase;
import com.wtm.shared.TemplateId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ApproveTemplateController {

    private final ApproveTemplateUseCase approve;

    ApproveTemplateController(ApproveTemplateUseCase approve) {
        this.approve = approve;
    }

    @PostMapping("/api/admin/templates/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void approve(@PathVariable UUID id) {
        approve.handle(new TemplateId(id));
    }
}

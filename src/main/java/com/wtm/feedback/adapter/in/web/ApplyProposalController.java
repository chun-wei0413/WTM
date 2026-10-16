package com.wtm.feedback.adapter.in.web;

import com.wtm.feedback.usecase.port.in.command.ResolveReportsUseCase;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Adopts the model's proposal as the meme's description and closes its reports. */
@RestController
class ApplyProposalController {

    private final ResolveReportsUseCase resolve;

    ApplyProposalController(ResolveReportsUseCase resolve) {
        this.resolve = resolve;
    }

    @PostMapping("/api/admin/reports/{templateId}/apply")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void apply(@PathVariable UUID templateId) {
        resolve.apply(templateId);
    }
}

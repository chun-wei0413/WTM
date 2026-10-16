package com.wtm.feedback.adapter.in.web;

import com.wtm.feedback.usecase.port.in.command.ResolveReportsUseCase;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Has the model look at the meme again, whatever the daily budget says. */
@RestController
class ReanalyzeMemeController {

    private final ResolveReportsUseCase resolve;

    ReanalyzeMemeController(ResolveReportsUseCase resolve) {
        this.resolve = resolve;
    }

    @PostMapping("/api/admin/reports/{templateId}/reanalyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void reanalyze(@PathVariable UUID templateId) {
        resolve.reanalyze(templateId);
    }
}

package com.wtm.feedback.adapter.in.web;

import com.wtm.feedback.usecase.port.in.command.ResolveReportsUseCase;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Closes the reports without changing the meme. */
@RestController
class DismissReportsController {

    private final ResolveReportsUseCase resolve;

    DismissReportsController(ResolveReportsUseCase resolve) {
        this.resolve = resolve;
    }

    @PostMapping("/api/admin/reports/{templateId}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void dismiss(@PathVariable UUID templateId) {
        resolve.dismiss(templateId);
    }
}

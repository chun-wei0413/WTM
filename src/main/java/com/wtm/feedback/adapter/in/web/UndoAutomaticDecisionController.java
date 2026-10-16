package com.wtm.feedback.adapter.in.web;

import com.wtm.feedback.usecase.port.in.command.ResolveReportsUseCase;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Takes back what the rules did to the meme: the earlier description, or the closed reports. */
@RestController
class UndoAutomaticDecisionController {

    private final ResolveReportsUseCase resolve;

    UndoAutomaticDecisionController(ResolveReportsUseCase resolve) {
        this.resolve = resolve;
    }

    @PostMapping("/api/admin/reports/{templateId}/undo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void undo(@PathVariable UUID templateId) {
        resolve.undoAutomatic(templateId);
    }
}

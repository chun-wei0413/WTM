package com.wtm.feedback.adapter.in.web;

import com.wtm.feedback.entity.ReportReason;
import com.wtm.feedback.usecase.port.in.command.SubmitReportUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Where a user says that a meme's description or tags do not fit it. */
@RestController
class SubmitReportController {

    private final SubmitReportUseCase submit;

    SubmitReportController(SubmitReportUseCase submit) {
        this.submit = submit;
    }

    @PostMapping("/api/reports")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void report(@Valid @RequestBody ReportRequest request, @AuthenticationPrincipal Jwt jwt) {
        submit.handle(new SubmitReportUseCase.Input(UUID.fromString(jwt.getSubject()), request.templateId(), request.reason(), request.comment()));
    }

    record ReportRequest(@NotNull UUID templateId, @NotNull ReportReason reason, @Size(max = 1000) String comment) {
    }
}

package com.wtm.adapter.in.web;

import com.wtm.application.report.ReportReason;
import com.wtm.application.report.SubmitReportHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where a user says that a meme's description or tags do not fit it.
 */
@RestController
@RequestMapping("/api/reports")
class ReportController {

    private final SubmitReportHandler submit;

    ReportController(SubmitReportHandler submit) {
        this.submit = submit;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void report(@Valid @RequestBody ReportRequest request, @AuthenticationPrincipal Jwt jwt) {
        submit.handle(UUID.fromString(jwt.getSubject()), request.templateId(), request.reason(), request.comment());
    }

    record ReportRequest(@NotNull UUID templateId, @NotNull ReportReason reason, @Size(max = 1000) String comment) {
    }
}

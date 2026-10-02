package com.wtm.adapter.in.web;

import com.wtm.application.report.ListReportCasesHandler;
import com.wtm.application.report.ListReportCasesHandler.ReportCase;
import com.wtm.application.report.ResolveReportsHandler;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The reported memes, with the vision model's new proposal for each (administrators only).
 */
@RestController
@RequestMapping("/api/admin/reports")
class AdminReportController {

    private final ListReportCasesHandler list;
    private final ResolveReportsHandler resolve;

    AdminReportController(ListReportCasesHandler list, ResolveReportsHandler resolve) {
        this.list = list;
        this.resolve = resolve;
    }

    @GetMapping
    List<ReportCase> cases() {
        return list.handle();
    }

    /** Adopts the model's proposal as the meme's description and closes its reports. */
    @PostMapping("/{templateId}/apply")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void apply(@PathVariable UUID templateId) {
        resolve.apply(templateId);
    }

    /** Closes the reports without changing the meme. */
    @PostMapping("/{templateId}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void dismiss(@PathVariable UUID templateId) {
        resolve.dismiss(templateId);
    }

    /** Has the model look at the meme again. */
    @PostMapping("/{templateId}/reanalyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void reanalyze(@PathVariable UUID templateId) {
        resolve.reanalyze(templateId);
    }
}

package com.wtm.feedback.adapter.in.web;

import com.wtm.feedback.entity.ReportCase;
import com.wtm.feedback.usecase.port.in.query.ListReportCasesUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The reported memes that still need a decision, with the vision model's new proposal for each (administrators only). */
@RestController
class ListReportCasesController {

    private final ListReportCasesUseCase list;

    ListReportCasesController(ListReportCasesUseCase list) {
        this.list = list;
    }

    @GetMapping("/api/admin/reports")
    List<ListReportCasesUseCase.ReportCase> cases() {
        return list.handle();
    }
}

package com.wtm.feedback.adapter.in.web;

import com.wtm.feedback.usecase.port.in.query.ListReportCasesUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** What the rules decided on their own in the last week, so it can be checked and taken back. */
@RestController
class ListAutomaticDecisionsController {

    private final ListReportCasesUseCase list;

    ListAutomaticDecisionsController(ListReportCasesUseCase list) {
        this.list = list;
    }

    @GetMapping("/api/admin/reports/automatic")
    List<ListReportCasesUseCase.AutomaticEntry> automatic() {
        return list.automatic();
    }
}

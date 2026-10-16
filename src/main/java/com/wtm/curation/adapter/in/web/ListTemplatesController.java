package com.wtm.curation.adapter.in.web;

import com.wtm.curation.entity.TemplateStatus;
import com.wtm.curation.usecase.port.in.query.ListTemplatesUseCase;
import com.wtm.curation.usecase.port.in.query.TemplateSummary;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ListTemplatesController {

    private final ListTemplatesUseCase list;

    ListTemplatesController(ListTemplatesUseCase list) {
        this.list = list;
    }

    @GetMapping("/api/admin/templates")
    List<TemplateSummary> list(@RequestParam(required = false) String status) {
        String normalized = status == null ? null : TemplateStatus.valueOf(status.toUpperCase()).name();
        return list.handle(normalized);
    }
}

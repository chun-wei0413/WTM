package com.wtm.curation.adapter.in.web;

import com.wtm.curation.usecase.port.in.query.GetTemplateUseCase;
import com.wtm.curation.usecase.port.in.query.TemplateView;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class GetTemplateController {

    private final GetTemplateUseCase get;

    GetTemplateController(GetTemplateUseCase get) {
        this.get = get;
    }

    @GetMapping("/api/admin/templates/{id}")
    TemplateView get(@PathVariable UUID id) {
        return get.handle(id);
    }
}

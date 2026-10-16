package com.wtm.curation.adapter.in.web;

import com.wtm.curation.usecase.port.in.command.ImportFromUrlUseCase;
import com.wtm.curation.usecase.port.in.command.IngestOutcome;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Adds one picture, given its address (administrators only). */
@RestController
class ImportFromUrlController {

    private final ImportFromUrlUseCase fromUrl;

    ImportFromUrlController(ImportFromUrlUseCase fromUrl) {
        this.fromUrl = fromUrl;
    }

    @PostMapping("/api/admin/collection/url")
    ImportedFile addUrl(@Valid @RequestBody UrlRequest request) {
        IngestOutcome outcome = fromUrl.handle(new ImportFromUrlUseCase.Input(request.url(), request.title(), request.pageUrl()));
        return new ImportedFile(request.url(), outcome.status(), outcome.templateId(), outcome.reason());
    }

    record UrlRequest(@NotBlank String url, String title, String pageUrl) {
    }
}

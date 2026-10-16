package com.wtm.curation.adapter.in.web;

import com.wtm.curation.usecase.port.in.command.DraftTemplateUseCase;
import com.wtm.shared.TemplateId;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Adds a picture as a new, draft template (administrators only). */
@RestController
class DraftTemplateController {

    private final DraftTemplateUseCase draft;

    DraftTemplateController(DraftTemplateUseCase draft) {
        this.draft = draft;
    }

    @PostMapping(value = "/api/admin/templates", consumes = "multipart/form-data")
    ResponseEntity<CreatedResponse> draft(@RequestParam @NotBlank String name,
                                          @RequestParam("file") MultipartFile file) throws IOException {
        TemplateId id = draft.handle(new DraftTemplateUseCase.Input(name, file.getBytes()));
        return ResponseEntity.created(URI.create("/api/admin/templates/" + id.value()))
                .body(new CreatedResponse(id.value()));
    }

    record CreatedResponse(UUID id) {
    }
}

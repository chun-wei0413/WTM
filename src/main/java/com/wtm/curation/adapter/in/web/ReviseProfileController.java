package com.wtm.curation.adapter.in.web;

import com.wtm.curation.usecase.port.in.command.ReviseProfileUseCase;
import com.wtm.shared.MemeProfile;
import com.wtm.shared.TemplateId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ReviseProfileController {

    private final ReviseProfileUseCase reviseProfile;

    ReviseProfileController(ReviseProfileUseCase reviseProfile) {
        this.reviseProfile = reviseProfile;
    }

    @PutMapping("/api/admin/templates/{id}/profile")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reviseProfile(@PathVariable UUID id, @RequestBody ProfileRequest request) {
        reviseProfile.handle(new ReviseProfileUseCase.Input(new TemplateId(id), new MemeProfile(request.meaning(),
                request.usageExamples(), request.emotions(), request.aliases(),
                request.imageText(), request.tags())));
    }

    record ProfileRequest(String meaning, List<String> usageExamples, List<String> emotions,
                          List<String> aliases, String imageText, List<String> tags) {
    }
}

package com.wtm.adapter.in.web;

import com.wtm.application.generation.GenerationView;
import com.wtm.application.generation.GetGenerationHandler;
import com.wtm.application.generation.KeepMemeHandler;
import com.wtm.application.generation.SubmitGenerationHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class GenerationController {

    private final SubmitGenerationHandler submit;
    private final GetGenerationHandler get;
    private final KeepMemeHandler keep;

    GenerationController(SubmitGenerationHandler submit, GetGenerationHandler get, KeepMemeHandler keep) {
        this.submit = submit;
        this.get = get;
        this.keep = keep;
    }

    /** Accepts the request and returns right away; poll the returned URL for the result. */
    @PostMapping("/api/generations")
    ResponseEntity<Accepted> submit(@Valid @RequestBody SubmitRequest request, @AuthenticationPrincipal Jwt jwt) {
        UUID jobId = submit.handle(userId(jwt), request.situation());
        return ResponseEntity.accepted()
                .location(URI.create("/api/generations/" + jobId))
                .body(new Accepted(jobId));
    }

    @GetMapping("/api/generations/{id}")
    GenerationView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return get.handle(userId(jwt), id);
    }

    @PostMapping("/api/memes/{id}/keep")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void keep(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        keep.handle(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record SubmitRequest(@NotBlank @Size(max = SubmitGenerationHandler.MAX_SITUATION_LENGTH * 2) String situation) {
    }

    record Accepted(UUID jobId) {
    }
}

package com.wtm.discovery.adapter.in.web;

import com.wtm.discovery.usecase.port.in.query.PickMemeUseCase;
import com.wtm.discovery.usecase.port.in.query.PickResult;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PickMemeController {

    private final PickMemeUseCase pick;

    PickMemeController(PickMemeUseCase pick) {
        this.pick = pick;
    }

    /** "Pick one for me": the closest memes to a situation, one chosen by a language model with its reason. */
    @PostMapping("/api/templates/pick")
    PickResult pick(@RequestBody PickRequest request, @AuthenticationPrincipal Jwt jwt) {
        return pick.handle(new PickMemeUseCase.Input(jwt.getSubject(), request.situation()));
    }

    record PickRequest(String situation) {
    }
}

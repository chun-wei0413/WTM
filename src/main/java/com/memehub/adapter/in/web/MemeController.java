package com.memehub.adapter.in.web;

import com.memehub.application.generation.ListMyMemesHandler;
import com.memehub.application.generation.MemeSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memes")
class MemeController {

    private final ListMyMemesHandler list;

    MemeController(ListMyMemesHandler list) {
        this.list = list;
    }

    /** The signed-in user's own memes; the ones they kept unless another status is asked for. */
    @GetMapping
    List<MemeSummary> mine(@RequestParam(required = false) String status,
                           @RequestParam(defaultValue = "50") int limit,
                           @AuthenticationPrincipal Jwt jwt) {
        return list.handle(UUID.fromString(jwt.getSubject()), status, limit);
    }
}

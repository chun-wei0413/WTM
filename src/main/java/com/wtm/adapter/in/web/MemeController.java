package com.wtm.adapter.in.web;

import com.wtm.application.generation.GetMemeImageHandler;
import com.wtm.application.generation.GetMemeImageHandler.MemeImage;
import com.wtm.application.generation.ListMyMemesHandler;
import com.wtm.application.generation.MemeSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memes")
class MemeController {

    private final ListMyMemesHandler list;
    private final GetMemeImageHandler image;

    MemeController(ListMyMemesHandler list, GetMemeImageHandler image) {
        this.list = list;
        this.image = image;
    }

    /** The signed-in user's own memes; the ones they kept unless another status is asked for. */
    @GetMapping
    List<MemeSummary> mine(@RequestParam(required = false) String status,
                           @RequestParam(defaultValue = "50") int limit,
                           @AuthenticationPrincipal Jwt jwt) {
        return list.handle(UUID.fromString(jwt.getSubject()), status, limit);
    }

    /** The image itself, served through the application so the caller's permission is checked. */
    @GetMapping("/{id}/image")
    ResponseEntity<byte[]> download(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        MemeImage found = image.handle(UUID.fromString(jwt.getSubject()), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(found.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(found.filename()).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(found.content());
    }
}

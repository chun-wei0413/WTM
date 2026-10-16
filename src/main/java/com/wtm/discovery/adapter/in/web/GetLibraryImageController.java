package com.wtm.discovery.adapter.in.web;

import com.wtm.discovery.usecase.port.in.query.GetLibraryImageUseCase;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class GetLibraryImageController {

    private final GetLibraryImageUseCase image;

    GetLibraryImageController(GetLibraryImageUseCase image) {
        this.image = image;
    }

    /**
     * The picture itself, served through the application: a browser can neither save a cross-origin
     * storage address as a file nor draw it on a canvas and read the result back.
     */
    @GetMapping("/api/library/{id}/image")
    ResponseEntity<byte[]> image(@PathVariable UUID id) {
        GetLibraryImageUseCase.ImageFile file = image.handle(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(file.content());
    }
}

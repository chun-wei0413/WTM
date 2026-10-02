package com.wtm.adapter.in.web;

import com.wtm.application.library.GetLibraryImageHandler;
import com.wtm.application.library.GetLibraryImageHandler.ImageFile;
import com.wtm.application.library.LibraryItem;
import com.wtm.application.library.RandomMemesHandler;
import com.wtm.application.library.SearchHistoryHandler;
import com.wtm.application.port.out.SearchLogPort.HotTerm;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Browsing the published library: random memes, the most common searches, and the pictures themselves.
 */
@RestController
@RequestMapping("/api/library")
class LibraryController {

    private final RandomMemesHandler random;
    private final SearchHistoryHandler history;
    private final GetLibraryImageHandler image;

    LibraryController(RandomMemesHandler random, SearchHistoryHandler history, GetLibraryImageHandler image) {
        this.random = random;
        this.history = history;
        this.image = image;
    }

    @GetMapping("/random")
    List<LibraryItem> random(@RequestParam(defaultValue = "12") @Min(1) @Max(50) int limit) {
        return random.handle(limit);
    }

    @GetMapping("/hot-searches")
    List<HotTerm> hotSearches(@RequestParam(defaultValue = "8") @Min(1) @Max(20) int limit) {
        return history.hot(limit);
    }

    /**
     * The picture itself, served through the application: a browser can neither save a cross-origin
     * storage address as a file nor draw it on a canvas and read the result back.
     */
    @GetMapping("/{id}/image")
    ResponseEntity<byte[]> image(@PathVariable UUID id) {
        ImageFile file = image.handle(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(file.content());
    }
}

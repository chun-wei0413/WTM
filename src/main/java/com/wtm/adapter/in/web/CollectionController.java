package com.wtm.adapter.in.web;

import com.wtm.application.collection.GetLibraryStatsHandler;
import com.wtm.application.collection.ImportFromUrlHandler;
import com.wtm.application.collection.ImportUploadsHandler;
import com.wtm.application.collection.ImportUploadsHandler.Result;
import com.wtm.application.collection.ImportUploadsHandler.Upload;
import com.wtm.application.collection.IngestOutcome;
import com.wtm.application.port.out.LibraryPort.LibraryStats;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Putting pictures into the meme library (administrators only).
 */
@RestController
@RequestMapping("/api/admin/collection")
class CollectionController {

    private final ImportUploadsHandler uploads;
    private final GetLibraryStatsHandler stats;
    private final ImportFromUrlHandler fromUrl;

    CollectionController(ImportUploadsHandler uploads, GetLibraryStatsHandler stats, ImportFromUrlHandler fromUrl) {
        this.uploads = uploads;
        this.stats = stats;
        this.fromUrl = fromUrl;
    }

    /** Adds many pictures at once, for example a whole folder picked in the browser. */
    @PostMapping(value = "/files", consumes = "multipart/form-data")
    List<FileResult> addFiles(@RequestParam("files") List<MultipartFile> files) throws IOException {
        List<Upload> batch = new ArrayList<>();
        for (MultipartFile file : files) {
            batch.add(new Upload(file.getOriginalFilename(), file.getBytes()));
        }
        return uploads.handle(batch).stream().map(FileResult::of).toList();
    }

    @GetMapping("/status")
    LibraryStats status() {
        return stats.handle();
    }

    /** Adds one picture, given its address. */
    @PostMapping("/url")
    FileResult addUrl(@Valid @RequestBody UrlRequest request) {
        IngestOutcome outcome = fromUrl.handle(request.url(), request.title(), request.pageUrl());
        return new FileResult(request.url(), outcome.status(), outcome.templateId(), outcome.reason());
    }

    record UrlRequest(@NotBlank String url, String title, String pageUrl) {
    }

    record FileResult(String fileName, IngestOutcome.Status status, UUID templateId, String reason) {

        static FileResult of(Result result) {
            IngestOutcome o = result.outcome();
            return new FileResult(result.fileName(), o.status(), o.templateId(), o.reason());
        }
    }
}

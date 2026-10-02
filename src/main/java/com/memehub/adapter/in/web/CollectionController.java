package com.memehub.adapter.in.web;

import com.memehub.application.collection.GetLibraryStatsHandler;
import com.memehub.application.collection.ImportUploadsHandler;
import com.memehub.application.collection.ImportUploadsHandler.Result;
import com.memehub.application.collection.ImportUploadsHandler.Upload;
import com.memehub.application.collection.IngestOutcome;
import com.memehub.application.port.out.LibraryPort.LibraryStats;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

    CollectionController(ImportUploadsHandler uploads, GetLibraryStatsHandler stats) {
        this.uploads = uploads;
        this.stats = stats;
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

    record FileResult(String fileName, IngestOutcome.Status status, UUID templateId, String reason) {

        static FileResult of(Result result) {
            IngestOutcome o = result.outcome();
            return new FileResult(result.fileName(), o.status(), o.templateId(), o.reason());
        }
    }
}

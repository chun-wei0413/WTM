package com.usethatmeme.adapter.in.web;

import com.usethatmeme.adapter.scheduling.CollectionRunner;
import com.usethatmeme.application.collection.GetLibraryStatsHandler;
import com.usethatmeme.application.collection.ImportFromUrlHandler;
import com.usethatmeme.application.collection.ListCollectionRunsHandler;
import com.usethatmeme.application.collection.ImportUploadsHandler;
import com.usethatmeme.application.collection.ImportUploadsHandler.Result;
import com.usethatmeme.application.collection.ImportUploadsHandler.Upload;
import com.usethatmeme.application.collection.IngestOutcome;
import com.usethatmeme.application.port.out.CollectionRunPort.RunView;
import com.usethatmeme.application.port.out.LibraryPort.LibraryStats;
import com.usethatmeme.application.port.out.MemeSourcePort;
import com.usethatmeme.application.port.out.MemeSourcePort.SourceOption;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    private final ListCollectionRunsHandler runs;
    private final CollectionRunner runner;
    private final List<MemeSourcePort> sources;

    CollectionController(ImportUploadsHandler uploads, GetLibraryStatsHandler stats, ImportFromUrlHandler fromUrl,
                         ListCollectionRunsHandler runs, CollectionRunner runner, List<MemeSourcePort> sources) {
        this.uploads = uploads;
        this.stats = stats;
        this.fromUrl = fromUrl;
        this.runs = runs;
        this.runner = runner;
        this.sources = sources;
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

    /** The places pictures can be collected from, and the settings each one understands. */
    @GetMapping("/sources")
    List<SourceView> sources() {
        return sources.stream()
                .map(s -> new SourceView(s.id(), s.displayName(), s.description(), s.options()))
                .toList();
    }

    /** Starts collecting in the background and returns at once; follow it through the run list. */
    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Started startRun(@Valid @RequestBody RunRequest request) {
        return new Started(runner.submit(request.source(), request.limit(),
                request.options() == null ? Map.of() : request.options()));
    }

    @GetMapping("/runs")
    List<RunView> recentRuns() {
        return runs.recent(20);
    }

    @GetMapping("/runs/{id}")
    RunView run(@PathVariable UUID id) {
        return runs.find(id).orElseThrow(() -> new IllegalArgumentException("Unknown collection run"));
    }

    record UrlRequest(@NotBlank String url, String title, String pageUrl) {
    }

    record RunRequest(@NotBlank String source, int limit, Map<String, String> options) {
    }

    record Started(UUID runId) {
    }

    record SourceView(String id, String name, String description, List<SourceOption> options) {
    }

    record FileResult(String fileName, IngestOutcome.Status status, UUID templateId, String reason) {

        static FileResult of(Result result) {
            IngestOutcome o = result.outcome();
            return new FileResult(result.fileName(), o.status(), o.templateId(), o.reason());
        }
    }
}

package com.wtm.application.collection;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds a batch of pictures the administrator picked from their own computer.
 * One bad file never stops the rest.
 */
public class ImportUploadsHandler {

    private final IngestMemeHandler ingest;

    public ImportUploadsHandler(IngestMemeHandler ingest) {
        this.ingest = ingest;
    }

    public record Upload(String fileName, byte[] content) {
    }

    public record Result(String fileName, IngestOutcome outcome) {
    }

    public List<Result> handle(List<Upload> uploads) {
        List<Result> results = new ArrayList<>();
        for (Upload upload : uploads) {
            IngestOutcome outcome;
            try {
                Origin origin = new Origin("UPLOAD", null, null, null, null);
                outcome = ingest.handle(upload.content(), upload.fileName(), origin);
            } catch (RuntimeException e) {
                outcome = IngestOutcome.rejected("Could not be added: " + e.getMessage());
            }
            results.add(new Result(upload.fileName(), outcome));
        }
        return results;
    }
}

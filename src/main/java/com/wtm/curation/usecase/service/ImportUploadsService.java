package com.wtm.curation.usecase.service;

import com.wtm.curation.entity.Origin;
import com.wtm.curation.usecase.port.in.command.ImportUploadsUseCase;
import com.wtm.curation.usecase.port.in.command.IngestMemeUseCase;
import com.wtm.curation.usecase.port.in.command.IngestOutcome;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds a batch of pictures the administrator picked from their own computer.
 * One bad file never stops the rest.
 */
public class ImportUploadsService implements ImportUploadsUseCase {

    private final IngestMemeService ingest;

    public ImportUploadsService(IngestMemeService ingest) {
        this.ingest = ingest;
    }

    @Override
    public List<Result> handle(List<Upload> uploads) {
        List<Result> results = new ArrayList<>();
        for (Upload upload : uploads) {
            IngestOutcome outcome;
            try {
                Origin origin = new Origin("UPLOAD", null, null, null, null);
                outcome = ingest.handle(new IngestMemeUseCase.Input(upload.content(), upload.fileName(), origin));
            } catch (RuntimeException e) {
                outcome = IngestOutcome.rejected("Could not be added: " + e.getMessage());
            }
            results.add(new Result(upload.fileName(), outcome));
        }
        return results;
    }
}

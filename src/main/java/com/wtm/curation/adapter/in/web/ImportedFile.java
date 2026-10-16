package com.wtm.curation.adapter.in.web;

import com.wtm.curation.usecase.port.in.command.ImportUploadsUseCase;
import com.wtm.curation.usecase.port.in.command.IngestOutcome;
import java.util.UUID;

/** What became of one picture an administrator added. */
record ImportedFile(String fileName, IngestOutcome.Status status, UUID templateId, String reason) {

    static ImportedFile of(ImportUploadsUseCase.Result result) {
        IngestOutcome o = result.outcome();
        return new ImportedFile(result.fileName(), o.status(), o.templateId(), o.reason());
    }
}

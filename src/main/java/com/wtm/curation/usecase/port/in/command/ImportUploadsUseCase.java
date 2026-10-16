package com.wtm.curation.usecase.port.in.command;

import java.util.List;

public interface ImportUploadsUseCase {

    record Upload(String fileName, byte[] content) {
    }

    record Result(String fileName, IngestOutcome outcome) {
    }

    List<Result> handle(List<Upload> uploads);
}

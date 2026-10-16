package com.wtm.curation.usecase.port.in.command;

import com.wtm.curation.entity.Origin;

public interface IngestMemeUseCase {

    IngestOutcome handle(Input input);

    /** @param suggestedName a title or file name for the picture, or null when there is none */
    record Input(byte[] image, String suggestedName, Origin origin) {
    }
}

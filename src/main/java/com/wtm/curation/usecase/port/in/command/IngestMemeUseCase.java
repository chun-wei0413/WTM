package com.wtm.curation.usecase.port.in.command;

import com.wtm.curation.entity.Origin;

public interface IngestMemeUseCase {

    /**
     * @param suggestedName a title or file name for the picture, or null when there is none
     */
    IngestOutcome handle(byte[] image, String suggestedName, Origin origin);
}

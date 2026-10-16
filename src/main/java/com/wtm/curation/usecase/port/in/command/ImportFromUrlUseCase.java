package com.wtm.curation.usecase.port.in.command;

public interface ImportFromUrlUseCase {

    /**
     * @throws FetchRefusedException when the address cannot or may not be downloaded
     */
    IngestOutcome handle(Input input);

    /** @param pageUrl the page it was found on, if the administrator knows it */
    record Input(String imageUrl, String title, String pageUrl) {
    }
}

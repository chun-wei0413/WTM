package com.wtm.curation.usecase.port.in.command;

public interface ImportFromUrlUseCase {

    /**
     * @param pageUrl the page it was found on, if the administrator knows it
     * @throws FetchRefusedException when the address cannot or may not be downloaded
     */
    IngestOutcome handle(String imageUrl, String title, String pageUrl);
}

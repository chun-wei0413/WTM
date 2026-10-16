package com.wtm.curation.usecase.service;

import com.wtm.curation.entity.Origin;
import com.wtm.curation.usecase.port.in.command.ImportFromUrlUseCase;
import com.wtm.curation.usecase.port.in.command.IngestMemeUseCase;
import com.wtm.curation.usecase.port.in.command.IngestOutcome;
import com.wtm.curation.usecase.port.out.fetch.RemoteFetchPort;
import com.wtm.curation.usecase.port.out.fetch.RemoteFetchPort.FetchedImage;

/**
 * Adds one picture the administrator found, given its address.
 */
public class ImportFromUrlService implements ImportFromUrlUseCase {

    private final RemoteFetchPort fetcher;
    private final IngestMemeService ingest;

    public ImportFromUrlService(RemoteFetchPort fetcher, IngestMemeService ingest) {
        this.fetcher = fetcher;
        this.ingest = ingest;
    }

    /**
     * @throws FetchRefusedException when the address cannot or may not be downloaded
     */
    @Override
    public IngestOutcome handle(Input input) {
        String imageUrl = input.imageUrl();
        String title = input.title();
        String pageUrl = input.pageUrl();
        String url = imageUrl == null ? "" : imageUrl.strip();
        if (url.isEmpty()) {
            throw new IllegalArgumentException("Please give the address of a picture");
        }
        FetchedImage image = fetcher.fetchImage(url);
        return ingest.handle(new IngestMemeUseCase.Input(image.content(), blankToNull(title), new Origin("URL", url, blankToNull(pageUrl), null, null)));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}

package com.memehub.application.collection;

import com.memehub.application.port.out.RemoteFetchPort;
import com.memehub.application.port.out.RemoteFetchPort.FetchedImage;

/**
 * Adds one picture the administrator found, given its address.
 */
public class ImportFromUrlHandler {

    private final RemoteFetchPort fetcher;
    private final IngestMemeHandler ingest;

    public ImportFromUrlHandler(RemoteFetchPort fetcher, IngestMemeHandler ingest) {
        this.fetcher = fetcher;
        this.ingest = ingest;
    }

    /**
     * @param pageUrl the page it was found on, if the administrator knows it
     * @throws FetchRefusedException when the address cannot or may not be downloaded
     */
    public IngestOutcome handle(String imageUrl, String title, String pageUrl) {
        String url = imageUrl == null ? "" : imageUrl.strip();
        if (url.isEmpty()) {
            throw new IllegalArgumentException("Please give the address of a picture");
        }
        FetchedImage image = fetcher.fetchImage(url);
        return ingest.handle(image.content(), blankToNull(title), new Origin("URL", url, blankToNull(pageUrl), null, null));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}

package com.usethatmeme.application.port.out;

import com.usethatmeme.application.collection.FetchRefusedException;

/**
 * Outbound port for reading things from other websites, politely: it honours robots.txt, spaces its
 * requests out, refuses addresses on private networks and caps what it will download.
 */
public interface RemoteFetchPort {

    /** Reads a web page. @throws FetchRefusedException when the download was refused or failed */
    String fetchText(String url);

    /**
     * Reads from a site's own published API. robots.txt governs the crawling of pages, and a site that
     * offers an API for programs states separate terms for it (a clear user agent, a slow pace), which
     * are followed instead. Everything else still applies: private addresses are refused, requests are
     * spaced out, and the size is capped.
     *
     * @throws FetchRefusedException when the download was refused or failed
     */
    String fetchApi(String url);

    /** @throws FetchRefusedException when the download was refused or failed, or it is not a picture */
    FetchedImage fetchImage(String url);

    record FetchedImage(byte[] content, String contentType) {
    }
}

package com.memehub.application.port.out;

import com.memehub.application.collection.FetchRefusedException;

/**
 * Outbound port for reading things from other websites, politely: it honours robots.txt, spaces its
 * requests out, refuses addresses on private networks and caps what it will download.
 */
public interface RemoteFetchPort {

    /** @throws FetchRefusedException when the download was refused or failed */
    String fetchText(String url);

    /** @throws FetchRefusedException when the download was refused or failed, or it is not a picture */
    FetchedImage fetchImage(String url);

    record FetchedImage(byte[] content, String contentType) {
    }
}

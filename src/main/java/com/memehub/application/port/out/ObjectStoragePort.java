package com.memehub.application.port.out;

import java.time.Duration;

/**
 * Outbound port for binary object storage (template images, rendered memes).
 */
public interface ObjectStoragePort {

    void put(String key, byte[] content, String contentType);

    byte[] get(String key);

    void delete(String key);

    /** A time-limited URL the browser can use to download the object directly. */
    String presignedGetUrl(String key, Duration validFor);
}

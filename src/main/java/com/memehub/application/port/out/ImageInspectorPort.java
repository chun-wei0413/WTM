package com.memehub.application.port.out;

import com.memehub.application.UnsupportedImageException;

/**
 * Outbound port that validates an uploaded image and reports its size and type.
 */
public interface ImageInspectorPort {

    ImageInfo inspect(byte[] content) throws UnsupportedImageException;

    record ImageInfo(int width, int height, String extension, String contentType) {
    }
}

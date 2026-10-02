package com.usethatmeme.application.port.out;

import com.usethatmeme.application.UnsupportedImageException;

/**
 * Outbound port that validates an uploaded image and reports its size and type.
 */
public interface ImageInspectorPort {

    ImageInfo inspect(byte[] content) throws UnsupportedImageException;

    record ImageInfo(int width, int height, String extension, String contentType) {
    }
}

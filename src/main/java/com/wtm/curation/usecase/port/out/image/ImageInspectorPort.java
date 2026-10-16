package com.wtm.curation.usecase.port.out.image;

import com.wtm.curation.usecase.port.in.UnsupportedImageException;

/**
 * Outbound port that validates an uploaded image and reports its size and type.
 */
public interface ImageInspectorPort {

    InspectedImage inspect(byte[] content) throws UnsupportedImageException;

    record InspectedImage(int width, int height, String extension, String contentType) {
    }
}

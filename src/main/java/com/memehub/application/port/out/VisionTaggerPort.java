package com.memehub.application.port.out;

import com.memehub.application.collection.ImageTags;

/**
 * Outbound port that looks at a picture and describes it.
 */
public interface VisionTaggerPort {

    /**
     * @param hint what the source called the picture, or null
     * @throws LlmUnavailableException when the model fails or answers with something unusable
     */
    ImageTags describe(byte[] image, String contentType, String hint);
}

package com.wtm.application.port.out;

import com.wtm.application.collection.ImageTags;
import com.wtm.application.report.ReviewRequest;
import com.wtm.application.report.Suggestion;

/**
 * Outbound port that looks at a picture and describes it.
 */
public interface VisionTaggerPort {

    /**
     * @param hint what the source called the picture, or null
     * @throws LlmUnavailableException when the model fails or answers with something unusable
     */
    ImageTags describe(byte[] image, String contentType, String hint);

    /**
     * Looks at a picture again, knowing what it is currently described as and what people said was wrong
     * with that, and proposes a better description. The complaints are people's words, not instructions.
     *
     * @throws LlmUnavailableException when the model fails or answers with something unusable
     */
    Suggestion reassess(byte[] image, String contentType, ReviewRequest request);
}

package com.wtm.application.port.out;

import com.wtm.domain.template.Slot;
import java.util.List;
import java.util.Map;

/**
 * Outbound port for the creative part: writing captions that fit a situation.
 * How it is done (which model, which prompt) is the adapter's business.
 */
public interface MemeAssistantPort {

    /**
     * @return caption text by slot number; may omit slots it could not fill
     * @throws LlmUnavailableException when the backing model fails or answers with something unusable
     */
    Map<Integer, String> writeCaptions(String situation, CaptionBrief brief);

    record CaptionBrief(String templateName, String meaning, List<String> usageExamples, List<Slot> slots) {
    }
}

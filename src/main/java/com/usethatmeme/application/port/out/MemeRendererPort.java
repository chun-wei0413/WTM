package com.usethatmeme.application.port.out;

import com.usethatmeme.domain.template.Slot;
import java.util.List;
import java.util.Map;

/**
 * Outbound port that draws captions onto a template image.
 */
public interface MemeRendererPort {

    RenderedImage render(byte[] templateImage, List<Slot> slots, Map<Integer, String> captions);

    record RenderedImage(byte[] content, String extension, String contentType) {
    }
}

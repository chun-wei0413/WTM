package com.wtm.application.report;

import com.wtm.domain.template.MemeProfile;
import java.util.List;

/**
 * What the vision model is told when it looks at a reported meme again.
 *
 * @param current how the meme is described now
 * @param complaints what people said was wrong, one line each, already labelled with their reason
 */
public record ReviewRequest(MemeProfile current, List<String> complaints) {
}

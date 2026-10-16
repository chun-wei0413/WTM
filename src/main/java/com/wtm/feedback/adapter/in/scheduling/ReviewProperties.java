package com.wtm.feedback.adapter.in.scheduling;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The review worker shares the tagging settings: it is the same model on the same graphics card.
 */
@ConfigurationProperties("wtm.tagging")
public record ReviewProperties(
        @DefaultValue("1") int maxConcurrent,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("PT10M") Duration timeout) {
}

package com.wtm.adapter.scheduling;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("wtm.tagging")
public record TaggingProperties(
        @DefaultValue("true") boolean workerEnabled,
        /** How many pictures are looked at at once. One is right for a single graphics card. */
        @DefaultValue("1") int maxConcurrent,
        @DefaultValue("3") int maxAttempts,
        /** A picture that has been "being tagged" longer than this belongs to a crashed worker. */
        @DefaultValue("PT10M") Duration timeout) {
}

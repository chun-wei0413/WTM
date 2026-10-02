package com.wtm.adapter.scheduling;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("wtm.index")
public record IndexProperties(
        @DefaultValue("true") boolean schedulerEnabled,
        @DefaultValue("PT15S") Duration syncInterval,
        @DefaultValue("50") int batchSize) {
}

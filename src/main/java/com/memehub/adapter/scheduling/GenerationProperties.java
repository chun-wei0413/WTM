package com.memehub.adapter.scheduling;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("memehub.generation")
public record GenerationProperties(
        @DefaultValue("true") boolean workerEnabled,
        /** How many jobs one application instance works on at the same time. */
        @DefaultValue("4") int maxConcurrentJobs,
        /** How many candidate memes each request produces. */
        @DefaultValue("3") int candidates,
        /** A running job older than this is assumed to belong to a crashed worker. */
        @DefaultValue("PT5M") Duration jobTimeout,
        @DefaultValue("3") int maxAttempts) {
}

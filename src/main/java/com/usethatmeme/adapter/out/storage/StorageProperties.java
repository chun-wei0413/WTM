package com.usethatmeme.adapter.out.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Connection settings for any S3-compatible store (MinIO locally, S3 or R2 later).
 */
@ConfigurationProperties("usethatmeme.storage")
public record StorageProperties(
        @DefaultValue("http://localhost:9000") String endpoint,
        @DefaultValue("us-east-1") String region,
        String accessKey,
        String secretKey,
        @DefaultValue("usethatmeme") String bucket) {
}

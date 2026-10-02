package com.wtm.adapter.out.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Connection settings for any S3-compatible store (MinIO locally, S3 or R2 later).
 */
@ConfigurationProperties("wtm.storage")
public record StorageProperties(
        @DefaultValue("http://localhost:9000") String endpoint,
        @DefaultValue("us-east-1") String region,
        String accessKey,
        String secretKey,
        @DefaultValue("wtm") String bucket,
        /**
         * The address a browser can reach the store at, used only in the temporary picture addresses handed to it.
         * Empty means the same as {@code endpoint}. Needed when the application reaches the store under a name that
         * only exists inside its own network (a container called {@code object-storage}).
         */
        @DefaultValue("") String publicEndpoint) {
}

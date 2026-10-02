package com.wtm.adapter.out.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class S3ObjectStorageAdapterTest {

    private static String presign(String endpoint, String publicEndpoint) {
        var adapter = new S3ObjectStorageAdapter(
                new StorageProperties(endpoint, "us-east-1", "access", "secret", "wtm", publicEndpoint));
        try {
            return adapter.presignedGetUrl("library/a.png", Duration.ofMinutes(5));
        } finally {
            adapter.close();
        }
    }

    @Test
    void pictureAddressesUseTheStoreAddressByDefault() {
        assertThat(presign("http://localhost:9000", "")).startsWith("http://localhost:9000/wtm/library/a.png?");
        assertThat(presign("http://localhost:9000", null)).startsWith("http://localhost:9000/wtm/library/a.png?");
    }

    @Test
    void pictureAddressesCanUseTheAddressABrowserReaches() {
        String url = presign("http://object-storage:9000", "http://localhost:9000");

        assertThat(url).startsWith("http://localhost:9000/wtm/library/a.png?");
        assertThat(url).doesNotContain("object-storage");
    }
}

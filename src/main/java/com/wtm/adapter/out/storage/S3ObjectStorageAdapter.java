package com.wtm.adapter.out.storage;

import com.wtm.application.port.out.ObjectStorageException;
import com.wtm.application.port.out.ObjectStoragePort;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
class S3ObjectStorageAdapter implements ObjectStoragePort {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private volatile boolean bucketReady;

    S3ObjectStorageAdapter(StorageProperties properties) {
        this.bucket = properties.bucket();
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        var region = Region.of(properties.region());
        var endpoint = URI.create(properties.endpoint());

        this.client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .forcePathStyle(true)
                // Some S3-compatible stores reject the newer default checksum trailers.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            ensureBucket();
            client.putObject(b -> b.bucket(bucket).key(key).contentType(contentType),
                    RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw new ObjectStorageException("Failed to store object " + key, e);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return client.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray();
        } catch (SdkException e) {
            throw new ObjectStorageException("Failed to read object " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(b -> b.bucket(bucket).key(key));
        } catch (SdkException e) {
            throw new ObjectStorageException("Failed to delete object " + key, e);
        }
    }

    @Override
    public String presignedGetUrl(String key, Duration validFor) {
        try {
            var request = GetObjectPresignRequest.builder()
                    .signatureDuration(validFor)
                    .getObjectRequest(g -> g.bucket(bucket).key(key))
                    .build();
            return presigner.presignGetObject(request).url().toString();
        } catch (SdkException e) {
            throw new ObjectStorageException("Failed to presign object " + key, e);
        }
    }

    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            try {
                client.headBucket(b -> b.bucket(bucket));
            } catch (S3Exception e) {
                if (e.statusCode() != 404) {
                    throw e;
                }
                client.createBucket(b -> b.bucket(bucket));
            }
            bucketReady = true;
        }
    }

    @PreDestroy
    void close() {
        client.close();
        presigner.close();
    }
}

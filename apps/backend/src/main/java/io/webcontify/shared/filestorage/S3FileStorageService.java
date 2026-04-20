package io.webcontify.shared.filestorage;

import java.io.InputStream;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class S3FileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorageService.class);

    private static final String PUBLIC_PREFIX = "config/";
    private static final String PRIVATE_PREFIX = "files/";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public S3FileStorageService(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    @Override
    public String uploadPublicAsset(String bucketName, String filename, InputStream content, String contentType) {
        String key = PUBLIC_PREFIX + filename;
        putObject(bucketName, key, content, contentType);
        log.debug("Uploaded public asset: bucket={}, key={}", bucketName, key);
        return key;
    }

    @Override
    public String uploadPrivateAsset(String bucketName, String filename, InputStream content, String contentType) {
        String key = PRIVATE_PREFIX + filename;
        putObject(bucketName, key, content, contentType);
        log.debug("Uploaded private asset: bucket={}, key={}", bucketName, key);
        return key;
    }

    @Override
    public String getPresignedUrl(String bucketName, String path, Duration expiry) {
        var presignedRequest = s3Presigner.presignGetObject(builder -> builder
                .signatureDuration(expiry)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(path)
                        .build()));
        String url = presignedRequest.url().toString();
        log.debug("Generated presigned URL: bucket={}, path={}, expiry={}", bucketName, path, expiry);
        return url;
    }

    @Override
    public InputStream getObject(String bucketName, String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        return s3Client.getObject(request);
    }

    @Override
    public void deleteAsset(String bucketName, String path) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(path)
                .build());
        log.debug("Deleted asset: bucket={}, path={}", bucketName, path);
    }

    @Override
    public void createBucket(String bucketName) {
        try {
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
        } catch (BucketAlreadyOwnedByYouException e) {
            log.info("Bucket already exists and is owned by us, continuing: {}", bucketName);
        }

        String policy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Sid": "PublicReadConfig",
                      "Effect": "Allow",
                      "Principal": "*",
                      "Action": "s3:GetObject",
                      "Resource": "arn:aws:s3:::%s/config/*"
                    }
                  ]
                }
                """.formatted(bucketName);

        s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
                .bucket(bucketName)
                .policy(policy)
                .build());

        log.info("Created bucket with public config policy: {}", bucketName);
    }

    private void putObject(String bucketName, String key, InputStream content, String contentType) {
        try {
            byte[] bytes = content.readAllBytes();
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(bytes));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Failed to read input stream for upload", e);
        }
    }
}

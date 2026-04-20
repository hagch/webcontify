package io.webcontify.shared.filestorage;

import java.io.InputStream;
import java.time.Duration;

public interface FileStorageService {

    String uploadPublicAsset(String bucketName, String filename, InputStream content, String contentType);

    String uploadPrivateAsset(String bucketName, String filename, InputStream content, String contentType);

    String getPresignedUrl(String bucketName, String path, Duration expiry);

    void deleteAsset(String bucketName, String path);

    void createBucket(String bucketName);
}

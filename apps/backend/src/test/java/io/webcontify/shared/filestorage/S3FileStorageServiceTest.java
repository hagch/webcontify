package io.webcontify.shared.filestorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3FileStorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3FileStorageService service;

    @BeforeEach
    void setUp() {
        service = new S3FileStorageService(s3Client, s3Presigner);
    }

    @Test
    void uploadPublicAsset_shouldPutObjectUnderConfigPrefix() {
        String bucketName = "tenant-test";
        String filename = "logo.png";
        InputStream content = new ByteArrayInputStream("image-data".getBytes());
        String contentType = "image/png";

        String result = service.uploadPublicAsset(bucketName, filename, content, contentType);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest captured = requestCaptor.getValue();
        assertThat(captured.bucket()).isEqualTo("tenant-test");
        assertThat(captured.key()).isEqualTo("config/logo.png");
        assertThat(captured.contentType()).isEqualTo("image/png");
        assertThat(result).isEqualTo("config/logo.png");
    }

    @Test
    void uploadPrivateAsset_shouldPutObjectUnderFilesPrefix() {
        String bucketName = "tenant-test";
        String filename = "document.pdf";
        InputStream content = new ByteArrayInputStream("pdf-data".getBytes());
        String contentType = "application/pdf";

        String result = service.uploadPrivateAsset(bucketName, filename, content, contentType);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest captured = requestCaptor.getValue();
        assertThat(captured.bucket()).isEqualTo("tenant-test");
        assertThat(captured.key()).isEqualTo("files/document.pdf");
        assertThat(captured.contentType()).isEqualTo("application/pdf");
        assertThat(result).isEqualTo("files/document.pdf");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPresignedUrl_shouldGeneratePresignedUrl() throws Exception {
        String bucketName = "tenant-test";
        String path = "files/document.pdf";
        Duration expiry = Duration.ofMinutes(15);

        PresignedGetObjectRequest presignedRequest = org.mockito.Mockito.mock(PresignedGetObjectRequest.class);
        URL presignedUrl = URI.create("https://s3.example.com/tenant-test/files/document.pdf?signed=true").toURL();
        when(presignedRequest.url()).thenReturn(presignedUrl);
        when(s3Presigner.presignGetObject(any(Consumer.class))).thenReturn(presignedRequest);

        String result = service.getPresignedUrl(bucketName, path, expiry);

        assertThat(result).isEqualTo("https://s3.example.com/tenant-test/files/document.pdf?signed=true");
    }

    @Test
    void deleteAsset_shouldCallDeleteObject() {
        String bucketName = "tenant-test";
        String path = "files/document.pdf";

        service.deleteAsset(bucketName, path);

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());

        DeleteObjectRequest captured = requestCaptor.getValue();
        assertThat(captured.bucket()).isEqualTo("tenant-test");
        assertThat(captured.key()).isEqualTo("files/document.pdf");
    }

    @Test
    void createBucket_shouldCallCreateBucketAndSetPolicy() {
        String bucketName = "tenant-test";

        service.createBucket(bucketName);

        ArgumentCaptor<CreateBucketRequest> createCaptor = ArgumentCaptor.forClass(CreateBucketRequest.class);
        verify(s3Client).createBucket(createCaptor.capture());
        assertThat(createCaptor.getValue().bucket()).isEqualTo("tenant-test");

        ArgumentCaptor<PutBucketPolicyRequest> policyCaptor = ArgumentCaptor.forClass(PutBucketPolicyRequest.class);
        verify(s3Client).putBucketPolicy(policyCaptor.capture());
        assertThat(policyCaptor.getValue().bucket()).isEqualTo("tenant-test");
        assertThat(policyCaptor.getValue().policy()).contains("config/*");
    }
}

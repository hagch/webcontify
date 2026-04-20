package io.webcontify.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.filestorage.FileStorageService;
import io.webcontify.shared.model.AuditInfo;
import io.webcontify.shared.model.SystemUser;
import io.webcontify.tenant.repository.TenantRepository;
import io.webcontify.tenant.repository.entity.TenantEntity;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantFileServiceTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private TenantRepository tenantRepository;

    private TenantFileService tenantFileService;

    private static final String S3_ENDPOINT = "http://localhost:9000";
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String TENANT_SLUG = "test-tenant";
    private static final String BUCKET_NAME = "tenant-" + TENANT_SLUG;

    @BeforeEach
    void setUp() {
        tenantFileService = new TenantFileService(fileStorageService, tenantRepository, S3_ENDPOINT);
    }

    @Test
    void shouldUploadPublicFileViaUploadPublicAsset() {
        TenantEntity tenant = createTenantEntity();
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(TENANT_ID))
                .thenReturn(Optional.of(tenant));
        when(fileStorageService.uploadPublicAsset(eq(BUCKET_NAME), eq("logo.png"), any(), eq("image/png")))
                .thenReturn("config/logo.png");

        InputStream content = new ByteArrayInputStream("image-data".getBytes());
        String storageKey = tenantFileService.uploadFile(TENANT_ID, content, "logo.png", "image/png", true);

        assertThat(storageKey).isEqualTo("config/logo.png");
        verify(fileStorageService).uploadPublicAsset(eq(BUCKET_NAME), eq("logo.png"), any(), eq("image/png"));
    }

    @Test
    void shouldUploadPrivateFileWithUuidPrefix() {
        TenantEntity tenant = createTenantEntity();
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(TENANT_ID))
                .thenReturn(Optional.of(tenant));
        when(fileStorageService.uploadPrivateAsset(eq(BUCKET_NAME), any(String.class), any(), eq("application/pdf")))
                .thenAnswer(invocation -> "files/" + invocation.getArgument(1));

        InputStream content = new ByteArrayInputStream("pdf-data".getBytes());
        String storageKey = tenantFileService.uploadFile(TENANT_ID, content, "document.pdf", "application/pdf", false);

        ArgumentCaptor<String> filenameCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileStorageService).uploadPrivateAsset(eq(BUCKET_NAME), filenameCaptor.capture(), any(), eq("application/pdf"));

        String capturedFilename = filenameCaptor.getValue();
        assertThat(capturedFilename).endsWith("/document.pdf");
        assertThat(capturedFilename).contains("/");
        // Verify UUID prefix format (uuid/filename)
        String uuidPart = capturedFilename.substring(0, capturedFilename.indexOf("/"));
        assertThat(uuidPart).hasSize(36); // UUID string length
    }

    @Test
    void shouldReturnDirectUrlForPublicStorageKey() {
        TenantEntity tenant = createTenantEntity();
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(TENANT_ID))
                .thenReturn(Optional.of(tenant));

        String url = tenantFileService.getFileUrl(TENANT_ID, "config/logo.png");

        assertThat(url).isEqualTo(S3_ENDPOINT + "/" + BUCKET_NAME + "/config/logo.png");
    }

    @Test
    void shouldReturnPresignedUrlForPrivateStorageKey() {
        TenantEntity tenant = createTenantEntity();
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(TENANT_ID))
                .thenReturn(Optional.of(tenant));
        String expectedUrl = "https://s3.example.com/presigned-url";
        when(fileStorageService.getPresignedUrl(eq(BUCKET_NAME), eq("files/uuid/document.pdf"), any(Duration.class)))
                .thenReturn(expectedUrl);

        String url = tenantFileService.getFileUrl(TENANT_ID, "files/uuid/document.pdf");

        assertThat(url).isEqualTo(expectedUrl);
        verify(fileStorageService).getPresignedUrl(eq(BUCKET_NAME), eq("files/uuid/document.pdf"), eq(Duration.ofMinutes(15)));
    }

    @Test
    void shouldCallDeleteAssetOnDelete() {
        TenantEntity tenant = createTenantEntity();
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(TENANT_ID))
                .thenReturn(Optional.of(tenant));

        tenantFileService.deleteFile(TENANT_ID, "files/uuid/document.pdf");

        verify(fileStorageService).deleteAsset(BUCKET_NAME, "files/uuid/document.pdf");
    }

    @Test
    void shouldThrowNotFoundWhenTenantDoesNotExist() {
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(TENANT_ID))
                .thenReturn(Optional.empty());

        InputStream content = new ByteArrayInputStream("data".getBytes());

        assertThatThrownBy(() -> tenantFileService.uploadFile(TENANT_ID, content, "file.txt", "text/plain", false))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("tenant.not_found");
    }

    @Test
    void shouldThrowNotFoundOnGetFileUrlWhenTenantDoesNotExist() {
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantFileService.getFileUrl(TENANT_ID, "config/logo.png"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("tenant.not_found");
    }

    @Test
    void shouldThrowNotFoundOnDeleteFileWhenTenantDoesNotExist() {
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantFileService.deleteFile(TENANT_ID, "files/uuid/document.pdf"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("tenant.not_found");
    }

    private TenantEntity createTenantEntity() {
        TenantEntity entity = new TenantEntity();
        entity.setId(TENANT_ID);
        entity.setName("Test Tenant");
        entity.setSlug(TENANT_SLUG);
        entity.setStatus("ACTIVE");

        AuditInfo auditInfo = new AuditInfo();
        auditInfo.setCreatedAt(OffsetDateTime.now());
        auditInfo.setCreatedBy(SystemUser.ID);
        auditInfo.setUpdatedAt(OffsetDateTime.now());
        auditInfo.setUpdatedBy(SystemUser.ID);
        entity.setAuditInfo(auditInfo);

        return entity;
    }
}

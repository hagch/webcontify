package io.webcontify.tenant.service;

import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.filestorage.FileStorageService;
import io.webcontify.tenant.repository.TenantRepository;
import io.webcontify.tenant.repository.entity.TenantEntity;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TenantFileService {

    private static final String CONFIG_PREFIX = "config/";
    private static final Duration PRESIGNED_URL_EXPIRY = Duration.ofMinutes(15);

    private final FileStorageService fileStorageService;
    private final TenantRepository tenantRepository;
    private final String s3Endpoint;

    public TenantFileService(
            FileStorageService fileStorageService,
            TenantRepository tenantRepository,
            @Value("${webcontify.s3.endpoint:}") String s3Endpoint) {
        this.fileStorageService = fileStorageService;
        this.tenantRepository = tenantRepository;
        this.s3Endpoint = s3Endpoint;
    }

    public String uploadFile(
            UUID tenantId, InputStream content, String filename, String contentType, boolean isPublic) {
        String bucketName = buildBucketName(tenantId);

        if (isPublic) {
            return fileStorageService.uploadPublicAsset(bucketName, filename, content, contentType);
        } else {
            String uniqueFilename = UUID.randomUUID() + "/" + filename;
            return fileStorageService.uploadPrivateAsset(bucketName, uniqueFilename, content, contentType);
        }
    }

    public String getFileUrl(UUID tenantId, String storageKey) {
        String bucketName = buildBucketName(tenantId);

        if (storageKey.startsWith(CONFIG_PREFIX)) {
            return s3Endpoint + "/" + bucketName + "/" + storageKey;
        }

        return fileStorageService.getPresignedUrl(bucketName, storageKey, PRESIGNED_URL_EXPIRY);
    }

    public void deleteFile(UUID tenantId, String storageKey) {
        String bucketName = buildBucketName(tenantId);
        fileStorageService.deleteAsset(bucketName, storageKey);
    }

    private String buildBucketName(UUID tenantId) {
        TenantEntity tenant = tenantRepository
                .findByIdAndAuditInfoDeletedAtIsNull(tenantId)
                .orElseThrow(() -> new NotFoundException("tenant.not_found"));
        return "tenant-" + tenant.getSlug();
    }
}

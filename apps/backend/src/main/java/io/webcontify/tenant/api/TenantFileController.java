package io.webcontify.tenant.api;

import io.webcontify.generated.api.TenantFilesApi;
import io.webcontify.generated.model.FileUploadResponse;
import io.webcontify.generated.model.FileUrlResponse;
import io.webcontify.shared.exception.BadRequestException;
import io.webcontify.shared.exception.ForbiddenException;
import io.webcontify.shared.security.TenantAwareAuthentication;
import io.webcontify.tenant.service.TenantFileService;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller für Tenant-File-Operationen (Upload, Download-URL, Löschen).
 * Die tenantId wird aus dem JWT-basierten SecurityContext gelesen — kein tenantId im URL-Pfad.
 * Platform Admins (ohne tenantId) erhalten 403 Forbidden.
 */
@RestController
public class TenantFileController implements TenantFilesApi {

    private static final Logger log = LoggerFactory.getLogger(TenantFileController.class);

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/x-icon", "image/svg+xml");
    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024; // 2MB

    private final TenantFileService tenantFileService;

    public TenantFileController(TenantFileService tenantFileService) {
        this.tenantFileService = tenantFileService;
    }

    @Override
    public ResponseEntity<FileUploadResponse> uploadFile(MultipartFile file, Boolean _public) {
        UUID tenantId = resolveTenantId();
        if (tenantId == null) {
            log.warn("Platform admin attempted to call file upload endpoint POST /api/v1/tenant/files");
            throw new ForbiddenException("tenant.platform_admin_not_allowed");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("file.size_exceeds_maximum");
        }
        if (file.getContentType() == null || !ALLOWED_IMAGE_TYPES.contains(file.getContentType())) {
            throw new BadRequestException("file.type_not_allowed");
        }

        boolean isPublic = _public != null && _public;

        try (InputStream inputStream = file.getInputStream()) {
            String storageKey = tenantFileService.uploadFile(
                    tenantId,
                    inputStream,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    isPublic);

            FileUploadResponse response = new FileUploadResponse();
            response.setStorageKey(storageKey);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IOException e) {
            throw new RuntimeException("file.upload_failed", e);
        }
    }

    @Override
    public ResponseEntity<FileUrlResponse> getFile(String storageKey) {
        UUID tenantId = resolveTenantId();
        if (tenantId == null) {
            log.warn("Platform admin attempted to call get file endpoint GET /api/v1/tenant/files/{}", storageKey);
            throw new ForbiddenException("tenant.platform_admin_not_allowed");
        }

        String url = tenantFileService.getFileUrl(tenantId, storageKey);
        FileUrlResponse response = new FileUrlResponse();
        response.setUrl(url);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteFile(String storageKey) {
        UUID tenantId = resolveTenantId();
        if (tenantId == null) {
            log.warn("Platform admin attempted to call delete file endpoint DELETE /api/v1/tenant/files/{}", storageKey);
            throw new ForbiddenException("tenant.platform_admin_not_allowed");
        }

        tenantFileService.deleteFile(tenantId, storageKey);
        return ResponseEntity.noContent().build();
    }

    /**
     * Liest die tenantId aus dem SecurityContext.
     * Gibt null zurück wenn der authentifizierte User ein Platform Admin ist (kein Tenant).
     */
    private UUID resolveTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof TenantAwareAuthentication tenantAuth) {
            return tenantAuth.getTenantInformation().tenantId();
        }
        return null;
    }
}

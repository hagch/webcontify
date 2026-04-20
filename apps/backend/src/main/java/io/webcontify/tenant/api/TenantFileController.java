package io.webcontify.tenant.api;

import io.webcontify.generated.api.TenantFilesApi;
import io.webcontify.generated.model.FileUploadResponse;
import io.webcontify.generated.model.FileUrlResponse;
import io.webcontify.tenant.service.TenantFileService;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class TenantFileController implements TenantFilesApi {

    // TODO: Replace with TenantContext.current().tenantId() when auth is ready
    private static final UUID PLACEHOLDER_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final TenantFileService tenantFileService;

    public TenantFileController(TenantFileService tenantFileService) {
        this.tenantFileService = tenantFileService;
    }

    @Override
    public ResponseEntity<FileUploadResponse> uploadFile(MultipartFile file, Boolean _public) {
        boolean isPublic = _public != null && _public;

        try (InputStream inputStream = file.getInputStream()) {
            String storageKey = tenantFileService.uploadFile(
                    PLACEHOLDER_TENANT_ID,
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
        String url = tenantFileService.getFileUrl(PLACEHOLDER_TENANT_ID, storageKey);

        FileUrlResponse response = new FileUrlResponse();
        response.setUrl(url);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteFile(String storageKey) {
        tenantFileService.deleteFile(PLACEHOLDER_TENANT_ID, storageKey);
        return ResponseEntity.noContent().build();
    }
}

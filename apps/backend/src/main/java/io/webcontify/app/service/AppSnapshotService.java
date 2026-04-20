package io.webcontify.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.model.AppSnapshot;
import io.webcontify.shared.filestorage.FileStorageService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AppSnapshotService {

    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    public AppSnapshotService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
        this.objectMapper = new ObjectMapper();
    }

    public AppSnapshot buildSnapshot(App app, String version) {
        return new AppSnapshot(
                app.getId().toString(),
                version,
                new AppSnapshot.AppSnapshotMetadata(app.getName(), app.getSlug(), app.getDescription()),
                List.of(),
                List.of());
    }

    public String uploadSnapshot(String bucketName, AppSnapshot snapshot) {
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            String filename = "snapshots/app-" + snapshot.appId() + "-v" + snapshot.version() + ".json";
            InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            return fileStorageService.uploadPrivateAsset(bucketName, filename, stream, "application/json");
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload snapshot", e);
        }
    }

    public AppSnapshot downloadSnapshot(String bucketName, String s3Key) {
        try {
            InputStream stream = fileStorageService.getObject(bucketName, s3Key);
            return objectMapper.readValue(stream, AppSnapshot.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to download snapshot", e);
        }
    }
}

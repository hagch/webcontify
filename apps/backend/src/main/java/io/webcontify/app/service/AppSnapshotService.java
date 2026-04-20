package io.webcontify.app.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.model.AppDependency;
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
    private final AppDependencyService dependencyService;
    private final ContentSnapshotProvider contentSnapshotProvider;
    private final ObjectMapper objectMapper;

    public AppSnapshotService(
            FileStorageService fileStorageService,
            AppDependencyService dependencyService,
            ContentSnapshotProvider contentSnapshotProvider) {
        this.fileStorageService = fileStorageService;
        this.dependencyService = dependencyService;
        this.contentSnapshotProvider = contentSnapshotProvider;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public AppSnapshot buildSnapshot(App app, String version) {
        var deps = dependencyService.listDependencies(app.getId());
        var snapshotDeps = deps.stream()
                .filter(AppDependency::isDirect)
                .map(d -> new AppSnapshot.AppSnapshotDependency(d.getDependsOnAppSlug(), ""))
                .toList();

        var contentDefinitions = contentSnapshotProvider.getSnapshotDefinitions(app.getId());

        var graphResponse = dependencyService.getDependencyGraph(app.getId());
        var graphNodes = graphResponse.getNodes().stream()
                .map(n -> new AppSnapshot.AppSnapshotGraphNode(
                        n.getId().toString(),
                        n.getAccessible(),
                        n.getName(),
                        n.getSlug(),
                        n.getStatus() != null ? n.getStatus().getValue() : null,
                        Boolean.TRUE.equals(n.getIsCurrent())))
                .toList();
        var graphEdges = graphResponse.getEdges().stream()
                .map(e -> new AppSnapshot.AppSnapshotGraphEdge(
                        e.getSource().toString(),
                        e.getTarget().toString(),
                        e.getDirect()))
                .toList();

        return new AppSnapshot(
                app.getId().toString(),
                version,
                new AppSnapshot.AppSnapshotMetadata(app.getName(), app.getSlug(), app.getDescription()),
                snapshotDeps,
                contentDefinitions,
                graphNodes,
                graphEdges);
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

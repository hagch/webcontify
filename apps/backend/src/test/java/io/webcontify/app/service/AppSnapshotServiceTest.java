package io.webcontify.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.webcontify.app.model.App;
import io.webcontify.app.model.AppSnapshot;
import io.webcontify.app.model.AppStatus;
import io.webcontify.shared.filestorage.FileStorageService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppSnapshotServiceTest {

    @Mock
    private FileStorageService fileStorageService;

    private AppSnapshotService appSnapshotService;

    @BeforeEach
    void setUp() {
        appSnapshotService = new AppSnapshotService(fileStorageService);
    }

    @Test
    void shouldBuildSnapshotWithAppMetadata() {
        UUID appId = UUID.randomUUID();
        App app = new App();
        app.setId(appId);
        app.setName("Test App");
        app.setSlug("test-app");
        app.setDescription("A test application");
        app.setStatus(AppStatus.ACTIVE);

        AppSnapshot snapshot = appSnapshotService.buildSnapshot(app, "1.0.0");

        assertThat(snapshot.appId()).isEqualTo(appId.toString());
        assertThat(snapshot.version()).isEqualTo("1.0.0");
        assertThat(snapshot.metadata().name()).isEqualTo("Test App");
        assertThat(snapshot.metadata().slug()).isEqualTo("test-app");
        assertThat(snapshot.metadata().description()).isEqualTo("A test application");
    }

    @Test
    void shouldBuildSnapshotWithEmptyListsForDefinitionsAndDependencies() {
        App app = new App();
        app.setId(UUID.randomUUID());
        app.setName("Test App");
        app.setSlug("test-app");

        AppSnapshot snapshot = appSnapshotService.buildSnapshot(app, "0.1.0");

        assertThat(snapshot.dependencies()).isEmpty();
        assertThat(snapshot.contentDefinitions()).isEmpty();
    }

    @Test
    void shouldUploadSnapshotToS3() {
        UUID appId = UUID.randomUUID();
        AppSnapshot snapshot = new AppSnapshot(
                appId.toString(),
                "1.0.0",
                new AppSnapshot.AppSnapshotMetadata("Test App", "test-app", "desc"),
                java.util.List.of(),
                java.util.List.of());

        String expectedKey = "files/snapshots/app-" + appId + "-v1.0.0.json";
        when(fileStorageService.uploadPrivateAsset(
                eq("tenant-test"), anyString(), any(InputStream.class), eq("application/json")))
                .thenReturn(expectedKey);

        String result = appSnapshotService.uploadSnapshot("tenant-test", snapshot);

        assertThat(result).isEqualTo(expectedKey);
        verify(fileStorageService).uploadPrivateAsset(
                eq("tenant-test"),
                eq("snapshots/app-" + appId + "-v1.0.0.json"),
                any(InputStream.class),
                eq("application/json"));
    }

    @Test
    void shouldDownloadSnapshotFromS3() {
        UUID appId = UUID.randomUUID();
        String json = """
                {
                    "appId": "%s",
                    "version": "1.0.0",
                    "metadata": {"name": "Test App", "slug": "test-app", "description": "desc"},
                    "dependencies": [],
                    "contentDefinitions": []
                }
                """.formatted(appId);

        when(fileStorageService.getObject("tenant-test", "files/snapshots/app-v1.0.0.json"))
                .thenReturn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        AppSnapshot result = appSnapshotService.downloadSnapshot("tenant-test", "files/snapshots/app-v1.0.0.json");

        assertThat(result.appId()).isEqualTo(appId.toString());
        assertThat(result.version()).isEqualTo("1.0.0");
        assertThat(result.metadata().name()).isEqualTo("Test App");
    }
}

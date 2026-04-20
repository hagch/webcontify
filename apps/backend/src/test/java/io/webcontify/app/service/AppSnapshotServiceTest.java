package io.webcontify.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.webcontify.app.model.App;
import io.webcontify.app.model.AppDependency;
import io.webcontify.app.model.AppSnapshot;
import io.webcontify.app.model.AppStatus;
import io.webcontify.shared.filestorage.FileStorageService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

    @Mock
    private AppDependencyService dependencyService;

    @Mock
    private ContentSnapshotProvider contentSnapshotProvider;

    private AppSnapshotService appSnapshotService;

    @BeforeEach
    void setUp() {
        appSnapshotService = new AppSnapshotService(fileStorageService, dependencyService, contentSnapshotProvider);
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

        when(dependencyService.listDependencies(appId)).thenReturn(List.of());
        when(contentSnapshotProvider.getSnapshotDefinitions(appId)).thenReturn(List.of());

        AppSnapshot snapshot = appSnapshotService.buildSnapshot(app, "1.0.0");

        assertThat(snapshot.appId()).isEqualTo(appId.toString());
        assertThat(snapshot.version()).isEqualTo("1.0.0");
        assertThat(snapshot.metadata().name()).isEqualTo("Test App");
        assertThat(snapshot.metadata().slug()).isEqualTo("test-app");
        assertThat(snapshot.metadata().description()).isEqualTo("A test application");
    }

    @Test
    void shouldBuildSnapshotWithEmptyListsWhenNoDependencies() {
        UUID appId = UUID.randomUUID();
        App app = new App();
        app.setId(appId);
        app.setName("Test App");
        app.setSlug("test-app");

        when(dependencyService.listDependencies(appId)).thenReturn(List.of());
        when(contentSnapshotProvider.getSnapshotDefinitions(appId)).thenReturn(List.of());

        AppSnapshot snapshot = appSnapshotService.buildSnapshot(app, "0.1.0");

        assertThat(snapshot.dependencies()).isEmpty();
        assertThat(snapshot.contentDefinitions()).isEmpty();
    }

    @Test
    void shouldBuildSnapshotWithDirectDependenciesOnly() {
        UUID appId = UUID.randomUUID();
        App app = new App();
        app.setId(appId);
        app.setName("Test App");
        app.setSlug("test-app");

        AppDependency directDep = new AppDependency();
        directDep.setAppId(appId);
        directDep.setDependsOnAppSlug("dep-app");
        directDep.setDirect(true);

        AppDependency transitiveDep = new AppDependency();
        transitiveDep.setAppId(appId);
        transitiveDep.setDependsOnAppSlug("transitive-app");
        transitiveDep.setDirect(false);

        when(dependencyService.listDependencies(appId)).thenReturn(List.of(directDep, transitiveDep));
        when(contentSnapshotProvider.getSnapshotDefinitions(appId)).thenReturn(List.of());

        AppSnapshot snapshot = appSnapshotService.buildSnapshot(app, "1.0.0");

        assertThat(snapshot.dependencies()).hasSize(1);
        assertThat(snapshot.dependencies().get(0).appSlug()).isEqualTo("dep-app");
    }

    @Test
    void shouldBuildSnapshotWithContentDefinitions() {
        UUID appId = UUID.randomUUID();
        App app = new App();
        app.setId(appId);
        app.setName("Test App");
        app.setSlug("test-app");

        UUID defId = UUID.randomUUID();
        UUID fieldId = UUID.randomUUID();
        var snapshotField = new AppSnapshot.AppSnapshotField(
                fieldId.toString(), "title", "TEXT", true, false, true, true,
                "{\"maxLength\":255}", 0);
        var snapshotDef = new AppSnapshot.AppSnapshotDefinition(
                defId.toString(), "Article", "article", "Blog articles",
                "{{title}}", List.of(snapshotField));

        when(dependencyService.listDependencies(appId)).thenReturn(List.of());
        when(contentSnapshotProvider.getSnapshotDefinitions(appId)).thenReturn(List.of(snapshotDef));

        AppSnapshot snapshot = appSnapshotService.buildSnapshot(app, "1.0.0");

        assertThat(snapshot.contentDefinitions()).hasSize(1);
        var def = snapshot.contentDefinitions().get(0);
        assertThat(def.id()).isEqualTo(defId.toString());
        assertThat(def.name()).isEqualTo("Article");
        assertThat(def.slug()).isEqualTo("article");
        assertThat(def.description()).isEqualTo("Blog articles");
        assertThat(def.displayFormat()).isEqualTo("{{title}}");
        assertThat(def.fields()).hasSize(1);

        var field = def.fields().get(0);
        assertThat(field.id()).isEqualTo(fieldId.toString());
        assertThat(field.fieldKey()).isEqualTo("title");
        assertThat(field.fieldType()).isEqualTo("TEXT");
        assertThat(field.required()).isTrue();
        assertThat(field.uniqueValue()).isFalse();
        assertThat(field.sortable()).isTrue();
        assertThat(field.filterable()).isTrue();
        assertThat(field.config()).isEqualTo("{\"maxLength\":255}");
        assertThat(field.position()).isZero();
    }

    @Test
    void shouldUploadSnapshotToS3() {
        UUID appId = UUID.randomUUID();
        AppSnapshot snapshot = new AppSnapshot(
                appId.toString(),
                "1.0.0",
                new AppSnapshot.AppSnapshotMetadata("Test App", "test-app", "desc"),
                List.of(),
                List.of());

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

    @Test
    void shouldDownloadSnapshotWithContentDefinitionsFromS3() {
        UUID appId = UUID.randomUUID();
        String json = """
                {
                    "appId": "%s",
                    "version": "1.0.0",
                    "metadata": {"name": "Test App", "slug": "test-app", "description": "desc"},
                    "dependencies": [],
                    "contentDefinitions": [
                        {
                            "id": "def-1",
                            "name": "Article",
                            "slug": "article",
                            "description": "Blog articles",
                            "displayFormat": "{{title}}",
                            "fields": [
                                {
                                    "id": "field-1",
                                    "fieldKey": "title",
                                    "fieldType": "TEXT",
                                    "required": true,
                                    "uniqueValue": false,
                                    "sortable": true,
                                    "filterable": true,
                                    "config": "{\\"maxLength\\":255}",
                                    "position": 0
                                }
                            ]
                        }
                    ]
                }
                """.formatted(appId);

        when(fileStorageService.getObject("tenant-test", "files/snapshots/app-v1.0.0.json"))
                .thenReturn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        AppSnapshot result = appSnapshotService.downloadSnapshot("tenant-test", "files/snapshots/app-v1.0.0.json");

        assertThat(result.contentDefinitions()).hasSize(1);
        var def = result.contentDefinitions().get(0);
        assertThat(def.id()).isEqualTo("def-1");
        assertThat(def.name()).isEqualTo("Article");
        assertThat(def.description()).isEqualTo("Blog articles");
        assertThat(def.displayFormat()).isEqualTo("{{title}}");
        assertThat(def.fields()).hasSize(1);
        assertThat(def.fields().get(0).fieldKey()).isEqualTo("title");
        assertThat(def.fields().get(0).fieldType()).isEqualTo("TEXT");
        assertThat(def.fields().get(0).required()).isTrue();
    }
}

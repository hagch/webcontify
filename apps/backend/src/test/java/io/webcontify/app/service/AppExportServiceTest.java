package io.webcontify.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.webcontify.app.model.*;
import io.webcontify.app.repository.AppVersionRepository;
import io.webcontify.app.repository.entity.AppVersionEntity;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.model.TenantInformation;
import io.webcontify.shared.security.TenantAwareAuthentication;
import io.webcontify.shared.security.TenantSlugResolver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AppExportServiceTest {

    private static final UUID TEST_TENANT_ID = UUID.randomUUID();
    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final UUID TEST_APP_ID = UUID.randomUUID();
    private static final UUID TEST_VERSION_ID = UUID.randomUUID();

    @Mock private AppVersionService appVersionService;
    @Mock private AppSnapshotService appSnapshotService;
    @Mock private AppDependencyService appDependencyService;
    @Mock private AppService appService;
    @Mock private AppVersionRepository appVersionRepository;
    @Mock private TenantSlugResolver tenantSlugResolver;

    private AppExportService appExportService;

    @BeforeEach
    void setUp() {
        TenantInformation info = new TenantInformation(TEST_TENANT_ID, false);
        var auth = new TenantAwareAuthentication(
                info, TEST_USER_ID, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        appExportService = new AppExportService(
                appVersionService,
                appSnapshotService,
                appDependencyService,
                appService,
                appVersionRepository,
                tenantSlugResolver);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldExportBlueprintWithMainApp() {
        AppVersion version = new AppVersion();
        version.setId(TEST_VERSION_ID);
        version.setAppId(TEST_APP_ID);
        version.setVersion("1.0.0");
        version.setS3Key("snapshots/app-" + TEST_APP_ID + "-v1.0.0.json");
        when(appVersionService.getVersion(TEST_APP_ID, TEST_VERSION_ID)).thenReturn(version);

        when(tenantSlugResolver.resolveSlugByTenantId(TEST_TENANT_ID))
                .thenReturn(Optional.of("test-tenant"));

        AppSnapshot snapshot = new AppSnapshot(
                TEST_APP_ID.toString(), "1.0.0",
                new AppSnapshot.AppSnapshotMetadata("My App", "my-app", "desc"),
                List.of(), List.of());
        when(appSnapshotService.downloadSnapshot("tenant-test-tenant", version.getS3Key()))
                .thenReturn(snapshot);

        App app = createApp(TEST_APP_ID, "My App", "my-app");
        when(appService.getById(TEST_APP_ID)).thenReturn(app);

        when(appDependencyService.listDependencies(TEST_APP_ID)).thenReturn(List.of());

        AppBlueprint result = appExportService.exportBlueprint(TEST_APP_ID, TEST_VERSION_ID);

        assertThat(result.apps()).hasSize(1);
        assertThat(result.apps().get(0).slug()).isEqualTo("my-app");
        assertThat(result.apps().get(0).version()).isEqualTo("1.0.0");
        assertThat(result.apps().get(0).snapshot()).isEqualTo(snapshot);
        assertThat(result.exportedAt()).isNotNull();
        assertThat(result.exportedBy()).isEqualTo(TEST_TENANT_ID);
    }

    @Test
    void shouldIncludeTransitiveDependencies() {
        UUID depAppId = UUID.randomUUID();
        UUID transDepAppId = UUID.randomUUID();

        AppVersion version = new AppVersion();
        version.setId(TEST_VERSION_ID);
        version.setAppId(TEST_APP_ID);
        version.setVersion("1.0.0");
        version.setS3Key("snapshots/main.json");
        when(appVersionService.getVersion(TEST_APP_ID, TEST_VERSION_ID)).thenReturn(version);

        when(tenantSlugResolver.resolveSlugByTenantId(TEST_TENANT_ID))
                .thenReturn(Optional.of("test-tenant"));

        AppSnapshot mainSnapshot = new AppSnapshot(
                TEST_APP_ID.toString(), "1.0.0",
                new AppSnapshot.AppSnapshotMetadata("App A", "app-a", null),
                List.of(new AppSnapshot.AppSnapshotDependency("app-b", "")),
                List.of());
        when(appSnapshotService.downloadSnapshot("tenant-test-tenant", "snapshots/main.json"))
                .thenReturn(mainSnapshot);

        App mainApp = createApp(TEST_APP_ID, "App A", "app-a");
        when(appService.getById(TEST_APP_ID)).thenReturn(mainApp);

        // Direct dep B and transitive dep C
        AppDependency depB = new AppDependency();
        depB.setDependsOnId(depAppId);
        depB.setDependsOnAppSlug("app-b");
        depB.setDirect(true);

        AppDependency depC = new AppDependency();
        depC.setDependsOnId(transDepAppId);
        depC.setDependsOnAppSlug("app-c");
        depC.setDirect(false);

        when(appDependencyService.listDependencies(TEST_APP_ID)).thenReturn(List.of(depB, depC));

        // Dep B version
        AppVersionEntity depBVersionEntity = new AppVersionEntity();
        depBVersionEntity.setVersion("0.5.0");
        depBVersionEntity.setS3Key("snapshots/dep-b.json");
        when(appVersionRepository.findTopByAppIdOrderByPublishedAtDesc(depAppId))
                .thenReturn(Optional.of(depBVersionEntity));

        AppSnapshot depBSnapshot = new AppSnapshot(
                depAppId.toString(), "0.5.0",
                new AppSnapshot.AppSnapshotMetadata("App B", "app-b", null),
                List.of(new AppSnapshot.AppSnapshotDependency("app-c", "")),
                List.of());
        when(appSnapshotService.downloadSnapshot("tenant-test-tenant", "snapshots/dep-b.json"))
                .thenReturn(depBSnapshot);
        when(appService.getById(depAppId)).thenReturn(createApp(depAppId, "App B", "app-b"));

        // Dep C version
        AppVersionEntity depCVersionEntity = new AppVersionEntity();
        depCVersionEntity.setVersion("0.2.0");
        depCVersionEntity.setS3Key("snapshots/dep-c.json");
        when(appVersionRepository.findTopByAppIdOrderByPublishedAtDesc(transDepAppId))
                .thenReturn(Optional.of(depCVersionEntity));

        AppSnapshot depCSnapshot = new AppSnapshot(
                transDepAppId.toString(), "0.2.0",
                new AppSnapshot.AppSnapshotMetadata("App C", "app-c", null),
                List.of(), List.of());
        when(appSnapshotService.downloadSnapshot("tenant-test-tenant", "snapshots/dep-c.json"))
                .thenReturn(depCSnapshot);
        when(appService.getById(transDepAppId)).thenReturn(createApp(transDepAppId, "App C", "app-c"));

        AppBlueprint result = appExportService.exportBlueprint(TEST_APP_ID, TEST_VERSION_ID);

        assertThat(result.apps()).hasSize(3);
        assertThat(result.apps().stream().map(AppBlueprint.BlueprintApp::slug))
                .containsExactly("app-a", "app-b", "app-c");
    }

    @Test
    void shouldThrowWhenVersionNotFound() {
        UUID missingVersionId = UUID.randomUUID();
        when(appVersionService.getVersion(TEST_APP_ID, missingVersionId))
                .thenThrow(new NotFoundException("app.version.not_found"));

        assertThatThrownBy(() -> appExportService.exportBlueprint(TEST_APP_ID, missingVersionId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("app.version.not_found");
    }

    private App createApp(UUID id, String name, String slug) {
        App app = new App();
        app.setId(id);
        app.setTenantId(TEST_TENANT_ID);
        app.setName(name);
        app.setSlug(slug);
        app.setStatus(AppStatus.ACTIVE);
        return app;
    }
}

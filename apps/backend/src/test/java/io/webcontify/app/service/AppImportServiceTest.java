package io.webcontify.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.webcontify.app.model.*;
import io.webcontify.app.repository.AppRepository;
import io.webcontify.shared.model.TenantInformation;
import io.webcontify.shared.security.TenantAwareAuthentication;
import io.webcontify.shared.security.TenantSlugResolver;
import io.webcontify.shared.util.SlugGenerator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
class AppImportServiceTest {

    private static final UUID TEST_TENANT_ID = UUID.randomUUID();

    @Mock private AppService appService;
    @Mock private AppVersionService appVersionService;
    @Mock private AppDependencyService appDependencyService;
    @Mock private AppSnapshotService appSnapshotService;
    @Mock private AppRepository appRepository;
    @Mock private SlugGenerator slugGenerator;
    @Mock private TenantSlugResolver tenantSlugResolver;

    private AppImportService appImportService;

    @BeforeEach
    void setUp() {
        TenantInformation info = new TenantInformation(TEST_TENANT_ID, false);
        var auth = new TenantAwareAuthentication(
                info, "test-user", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        appImportService = new AppImportService(
                appService,
                appVersionService,
                appDependencyService,
                appSnapshotService,
                appRepository,
                slugGenerator,
                tenantSlugResolver);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldDetectSlugCollision() {
        AppBlueprint blueprint = new AppBlueprint(
                OffsetDateTime.now(), TEST_TENANT_ID,
                List.of(new AppBlueprint.BlueprintApp(
                        "my-app", "My App", "desc", "1.0.0", List.of(),
                        createSnapshot("my-app"))));

        when(appRepository.existsBySlugAndTenantIdAndAuditInfoDeletedAtIsNull("my-app", TEST_TENANT_ID))
                .thenReturn(true);
        when(appRepository.existsBySlugAndTenantIdAndAuditInfoDeletedAtIsNull("my-app-imported", TEST_TENANT_ID))
                .thenReturn(false);

        ImportPreview result = appImportService.analyzeBlueprint(blueprint);

        assertThat(result.canImport()).isTrue();
        assertThat(result.apps()).hasSize(1);
        assertThat(result.apps().get(0).hasSlugCollision()).isTrue();
        assertThat(result.apps().get(0).suggestedSlug()).isEqualTo("my-app-imported");
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("my-app");
    }

    @Test
    void shouldSuggestAlternativeSlug() {
        AppBlueprint blueprint = new AppBlueprint(
                OffsetDateTime.now(), TEST_TENANT_ID,
                List.of(new AppBlueprint.BlueprintApp(
                        "my-app", "My App", "desc", "1.0.0", List.of(),
                        createSnapshot("my-app"))));

        when(appRepository.existsBySlugAndTenantIdAndAuditInfoDeletedAtIsNull("my-app", TEST_TENANT_ID))
                .thenReturn(true);
        when(appRepository.existsBySlugAndTenantIdAndAuditInfoDeletedAtIsNull("my-app-imported", TEST_TENANT_ID))
                .thenReturn(true);
        when(appRepository.existsBySlugAndTenantIdAndAuditInfoDeletedAtIsNull("my-app-imported-2", TEST_TENANT_ID))
                .thenReturn(false);

        ImportPreview result = appImportService.analyzeBlueprint(blueprint);

        assertThat(result.apps().get(0).suggestedSlug()).isEqualTo("my-app-imported-2");
    }

    @Test
    void shouldImportSingleApp() {
        AppSnapshot snapshot = createSnapshot("single-app");
        AppBlueprint blueprint = new AppBlueprint(
                OffsetDateTime.now(), TEST_TENANT_ID,
                List.of(new AppBlueprint.BlueprintApp(
                        "single-app", "Single App", "desc", "1.0.0", List.of(), snapshot)));

        UUID createdAppId = UUID.randomUUID();
        App createdApp = new App();
        createdApp.setId(createdAppId);
        createdApp.setName("Single App");
        createdApp.setSlug("single-app");
        when(appService.create(any(App.class))).thenReturn(createdApp);

        AppVersion publishedVersion = new AppVersion();
        publishedVersion.setVersion("1.0.0");
        when(appVersionService.publish(createdAppId, "1.0.0", "Imported version"))
                .thenReturn(publishedVersion);

        List<App> result = appImportService.executeImport(blueprint, Map.of());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Single App");
        verify(appService).create(any(App.class));
        verify(appVersionService).publish(createdAppId, "1.0.0", "Imported version");
    }

    @Test
    void shouldImportInDependencyOrder() {
        AppSnapshot snapshotA = createSnapshot("app-a");
        AppSnapshot snapshotB = createSnapshot("app-b");

        // A depends on B, so B must be created first
        AppBlueprint blueprint = new AppBlueprint(
                OffsetDateTime.now(), TEST_TENANT_ID,
                List.of(
                        new AppBlueprint.BlueprintApp(
                                "app-a", "App A", "desc", "1.0.0", List.of("app-b"), snapshotA),
                        new AppBlueprint.BlueprintApp(
                                "app-b", "App B", "desc", "0.5.0", List.of(), snapshotB)));

        UUID appBId = UUID.randomUUID();
        App createdB = new App();
        createdB.setId(appBId);
        createdB.setName("App B");
        createdB.setSlug("app-b");

        UUID appAId = UUID.randomUUID();
        App createdA = new App();
        createdA.setId(appAId);
        createdA.setName("App A");
        createdA.setSlug("app-a");

        // B should be created first, then A
        when(appService.create(any(App.class)))
                .thenReturn(createdB)
                .thenReturn(createdA);

        AppVersion versionB = new AppVersion();
        versionB.setVersion("0.5.0");
        when(appVersionService.publish(appBId, "0.5.0", "Imported version")).thenReturn(versionB);

        AppVersion versionA = new AppVersion();
        versionA.setVersion("1.0.0");
        when(appVersionService.publish(appAId, "1.0.0", "Imported version")).thenReturn(versionA);

        List<App> result = appImportService.executeImport(blueprint, Map.of());

        assertThat(result).hasSize(2);
        // B comes before A in result (topological order)
        assertThat(result.get(0).getSlug()).isEqualTo("app-b");
        assertThat(result.get(1).getSlug()).isEqualTo("app-a");

        // Verify dependency wiring: A depends on B
        verify(appDependencyService).addDependency(appAId, appBId);
    }

    private AppSnapshot createSnapshot(String slug) {
        return new AppSnapshot(
                UUID.randomUUID().toString(), "1.0.0",
                new AppSnapshot.AppSnapshotMetadata("App", slug, null),
                List.of(), List.of());
    }
}

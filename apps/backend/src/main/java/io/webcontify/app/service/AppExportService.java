package io.webcontify.app.service;

import io.webcontify.app.model.*;
import io.webcontify.app.repository.AppVersionRepository;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.security.TenantContext;
import io.webcontify.shared.security.TenantSlugResolver;
import java.time.OffsetDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AppExportService {

    private static final Logger log = LoggerFactory.getLogger(AppExportService.class);

    private final AppVersionService appVersionService;
    private final AppSnapshotService appSnapshotService;
    private final AppDependencyService appDependencyService;
    private final AppService appService;
    private final AppVersionRepository appVersionRepository;
    private final TenantSlugResolver tenantSlugResolver;

    public AppExportService(
            AppVersionService appVersionService,
            AppSnapshotService appSnapshotService,
            AppDependencyService appDependencyService,
            AppService appService,
            AppVersionRepository appVersionRepository,
            TenantSlugResolver tenantSlugResolver) {
        this.appVersionService = appVersionService;
        this.appSnapshotService = appSnapshotService;
        this.appDependencyService = appDependencyService;
        this.appService = appService;
        this.appVersionRepository = appVersionRepository;
        this.tenantSlugResolver = tenantSlugResolver;
    }

    public AppBlueprint exportBlueprint(UUID appId, UUID versionId) {
        var tenantInfo = TenantContext.getTenantInformation();

        AppVersion version = appVersionService.getVersion(appId, versionId);

        String tenantSlug = tenantSlugResolver.resolveSlugByTenantId(tenantInfo.tenantId())
                .orElseThrow(() -> new NotFoundException("tenant.not_found"));
        String bucketName = "tenant-" + tenantSlug;

        AppSnapshot mainSnapshot = appSnapshotService.downloadSnapshot(bucketName, version.getS3Key());
        App mainApp = appService.getById(appId);

        List<AppBlueprint.BlueprintApp> blueprintApps = new ArrayList<>();
        blueprintApps.add(toBlueprintApp(mainApp, version.getVersion(), mainSnapshot));

        List<AppDependency> dependencies = appDependencyService.listDependencies(appId);
        for (AppDependency dep : dependencies) {
            try {
                var latestVersion = appVersionRepository
                        .findTopByAppIdOrderByPublishedAtDesc(dep.getDependsOnId());
                if (latestVersion.isEmpty()) {
                    log.warn("Dependency app '{}' has no published version, skipping from export",
                            dep.getDependsOnAppSlug());
                    continue;
                }

                var depVersion = latestVersion.get();
                AppSnapshot depSnapshot = appSnapshotService.downloadSnapshot(
                        bucketName, depVersion.getS3Key());
                App depApp = appService.getById(dep.getDependsOnId());
                blueprintApps.add(toBlueprintApp(depApp, depVersion.getVersion(), depSnapshot));
            } catch (Exception e) {
                log.warn("Failed to export dependency '{}': {}",
                        dep.getDependsOnAppSlug(), e.getMessage());
            }
        }

        return new AppBlueprint(
                OffsetDateTime.now(),
                tenantInfo.tenantId(),
                blueprintApps);
    }

    private AppBlueprint.BlueprintApp toBlueprintApp(App app, String version, AppSnapshot snapshot) {
        List<String> depSlugs = snapshot.dependencies().stream()
                .map(AppSnapshot.AppSnapshotDependency::appSlug)
                .toList();
        return new AppBlueprint.BlueprintApp(
                app.getSlug(),
                app.getName(),
                app.getDescription(),
                version,
                depSlugs,
                snapshot);
    }
}

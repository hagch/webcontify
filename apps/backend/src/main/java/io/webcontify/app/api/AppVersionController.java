package io.webcontify.app.api;

import io.webcontify.app.mapper.AppMapper;
import io.webcontify.app.mapper.AppVersionMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.model.AppVersion;
import io.webcontify.app.service.AppService;
import io.webcontify.app.service.AppVersionService;
import io.webcontify.generated.api.AppVersionApi;
import io.webcontify.generated.model.*;
import io.webcontify.identity.model.CheckPermissionRequest;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.identity.service.UserSummaryEnricher;
import io.webcontify.shared.security.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppVersionController implements AppVersionApi {

    private final AppVersionService appVersionService;
    private final AppVersionMapper appVersionMapper;
    private final AppMapper appMapper;
    private final UserSummaryEnricher userSummaryEnricher;
    private final AppService appService;
    private final PermissionService permissionService;

    public AppVersionController(
            AppVersionService appVersionService,
            AppVersionMapper appVersionMapper,
            AppMapper appMapper,
            UserSummaryEnricher userSummaryEnricher,
            AppService appService,
            PermissionService permissionService) {
        this.appVersionService = appVersionService;
        this.appVersionMapper = appVersionMapper;
        this.appMapper = appMapper;
        this.userSummaryEnricher = userSummaryEnricher;
        this.appService = appService;
        this.permissionService = permissionService;
    }

    @Override
    public ResponseEntity<AppVersionResponse> publishApp(
            String appId, PublishAppRequest publishAppRequest) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("admin", app.getId());
        AppVersion version =
                appVersionService.publish(app.getId(), publishAppRequest.getVersion(), publishAppRequest.getReleaseNotes());
        AppVersionResponse response = appVersionMapper.toResponse(version);
        enrichUserSummary(response);
        return ResponseEntity.created(
                        URI.create("/api/v1/apps/" + app.getId() + "/versions/" + version.getId()))
                .body(response);
    }

    @Override
    public ResponseEntity<AppVersionListResponse> listAppVersions(String appId) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("viewer", app.getId());
        List<AppVersion> versions = appVersionService.listVersions(app.getId());
        List<AppVersionResponse> items = versions.stream().map(appVersionMapper::toResponse).toList();
        enrichUserSummaries(items);
        AppVersionListResponse response = new AppVersionListResponse();
        response.setItems(items);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<AppVersionResponse> getAppVersion(String appId, UUID versionId) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("viewer", app.getId());
        AppVersion version = appVersionService.getVersion(app.getId(), versionId);
        AppVersionResponse response = appVersionMapper.toResponse(version);
        enrichUserSummary(response);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<VersionDiffResponse> getVersionDiff(String appId, UUID versionId) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("viewer", app.getId());
        VersionDiffResponse diff = appVersionService.getVersionDiff(app.getId(), versionId);
        return ResponseEntity.ok(diff);
    }

    @Override
    public ResponseEntity<VersionSchemaResponse> getVersionSchema(String appId, UUID versionId) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("viewer", app.getId());
        VersionSchemaResponse schema = appVersionService.getVersionSchema(app.getId(), versionId);
        return ResponseEntity.ok(schema);
    }

    @Override
    public ResponseEntity<AppResponse> rollbackAppVersion(String appId, UUID versionId) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("admin", app.getId());
        App rolledBack = appVersionService.rollback(app.getId(), versionId);
        return ResponseEntity.ok(appMapper.toResponse(rolledBack));
    }

    @Override
    public ResponseEntity<VersionSuggestionResponse> suggestNextVersion(String appId) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("viewer", app.getId());
        String suggested = appVersionService.suggestNextVersion(app.getId());
        VersionSuggestionResponse response = new VersionSuggestionResponse();
        response.setSuggestedVersion(suggested);
        return ResponseEntity.ok(response);
    }

    private void checkAppPermission(String action, UUID resourceId) {
        var tenantInfo = TenantContext.getTenantInformation();
        if (tenantInfo.platformAdmin()) {
            return;
        }
        UUID userId = TenantContext.getCurrentUserId();
        var request = new CheckPermissionRequest(userId, action, "app", resourceId);
        if (!permissionService.check(request)) {
            throw new AccessDeniedException("Insufficient permissions");
        }
    }

    private void enrichUserSummary(AppVersionResponse response) {
        if (response.getPublishedBy() != null) {
            response.setPublishedByUser(userSummaryEnricher.resolve(response.getPublishedBy()));
        }
    }

    private void enrichUserSummaries(List<AppVersionResponse> items) {
        var userIds = items.stream()
                .map(AppVersionResponse::getPublishedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        var userMap = userSummaryEnricher.resolveAll(userIds);
        items.forEach(item -> {
            if (item.getPublishedBy() != null) {
                item.setPublishedByUser(userMap.get(item.getPublishedBy()));
            }
        });
    }
}

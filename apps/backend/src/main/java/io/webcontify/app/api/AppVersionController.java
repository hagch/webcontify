package io.webcontify.app.api;

import io.webcontify.app.mapper.AppMapper;
import io.webcontify.app.mapper.AppVersionMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.model.AppVersion;
import io.webcontify.app.service.AppVersionService;
import io.webcontify.generated.api.AppVersionApi;
import io.webcontify.generated.model.*;
import io.webcontify.identity.security.RequiresPermission;
import io.webcontify.identity.service.UserSummaryEnricher;
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

    public AppVersionController(
            AppVersionService appVersionService,
            AppVersionMapper appVersionMapper,
            AppMapper appMapper,
            UserSummaryEnricher userSummaryEnricher) {
        this.appVersionService = appVersionService;
        this.appVersionMapper = appVersionMapper;
        this.appMapper = appMapper;
        this.userSummaryEnricher = userSummaryEnricher;
    }

    @Override
    @RequiresPermission(action = "admin", resource = "app", resourceId = "#appId")
    public ResponseEntity<AppVersionResponse> publishApp(
            UUID appId, PublishAppRequest publishAppRequest) {
        AppVersion version =
                appVersionService.publish(appId, publishAppRequest.getVersion(), publishAppRequest.getReleaseNotes());
        AppVersionResponse response = appVersionMapper.toResponse(version);
        enrichUserSummary(response);
        return ResponseEntity.created(
                        URI.create("/api/v1/apps/" + appId + "/versions/" + version.getId()))
                .body(response);
    }

    @Override
    @RequiresPermission(action = "viewer", resource = "app", resourceId = "#appId")
    public ResponseEntity<AppVersionListResponse> listAppVersions(UUID appId) {
        List<AppVersion> versions = appVersionService.listVersions(appId);
        List<AppVersionResponse> items = versions.stream().map(appVersionMapper::toResponse).toList();
        enrichUserSummaries(items);
        AppVersionListResponse response = new AppVersionListResponse();
        response.setItems(items);
        return ResponseEntity.ok(response);
    }

    @Override
    @RequiresPermission(action = "viewer", resource = "app", resourceId = "#appId")
    public ResponseEntity<AppVersionResponse> getAppVersion(UUID appId, UUID versionId) {
        AppVersion version = appVersionService.getVersion(appId, versionId);
        AppVersionResponse response = appVersionMapper.toResponse(version);
        enrichUserSummary(response);
        return ResponseEntity.ok(response);
    }

    @Override
    @RequiresPermission(action = "viewer", resource = "app", resourceId = "#appId")
    public ResponseEntity<VersionDiffResponse> getVersionDiff(UUID appId, UUID versionId) {
        VersionDiffResponse diff = appVersionService.getVersionDiff(appId, versionId);
        return ResponseEntity.ok(diff);
    }

    @Override
    @RequiresPermission(action = "admin", resource = "app", resourceId = "#appId")
    public ResponseEntity<AppResponse> rollbackAppVersion(UUID appId, UUID versionId) {
        App app = appVersionService.rollback(appId, versionId);
        return ResponseEntity.ok(appMapper.toResponse(app));
    }

    @Override
    @RequiresPermission(action = "viewer", resource = "app", resourceId = "#appId")
    public ResponseEntity<VersionSuggestionResponse> suggestNextVersion(UUID appId) {
        String suggested = appVersionService.suggestNextVersion(appId);
        VersionSuggestionResponse response = new VersionSuggestionResponse();
        response.setSuggestedVersion(suggested);
        return ResponseEntity.ok(response);
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

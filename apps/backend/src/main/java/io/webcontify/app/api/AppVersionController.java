package io.webcontify.app.api;

import io.webcontify.app.mapper.AppMapper;
import io.webcontify.app.mapper.AppVersionMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.model.AppVersion;
import io.webcontify.app.service.AppVersionService;
import io.webcontify.generated.api.AppVersionApi;
import io.webcontify.generated.model.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

// TODO: Add @RequiresPermission when app type is added to OpenFGA (Sub-Plan 4e)
@RestController
public class AppVersionController implements AppVersionApi {

    private final AppVersionService appVersionService;
    private final AppVersionMapper appVersionMapper;
    private final AppMapper appMapper;

    public AppVersionController(
            AppVersionService appVersionService,
            AppVersionMapper appVersionMapper,
            AppMapper appMapper) {
        this.appVersionService = appVersionService;
        this.appVersionMapper = appVersionMapper;
        this.appMapper = appMapper;
    }

    @Override
    public ResponseEntity<AppVersionResponse> publishApp(
            UUID appId, PublishAppRequest publishAppRequest) {
        AppVersion version =
                appVersionService.publish(appId, publishAppRequest.getVersion(), publishAppRequest.getReleaseNotes());
        AppVersionResponse response = appVersionMapper.toResponse(version);
        return ResponseEntity.created(
                        URI.create("/api/v1/apps/" + appId + "/versions/" + version.getId()))
                .body(response);
    }

    @Override
    public ResponseEntity<AppVersionListResponse> listAppVersions(UUID appId) {
        List<AppVersion> versions = appVersionService.listVersions(appId);
        AppVersionListResponse response = new AppVersionListResponse();
        response.setItems(versions.stream().map(appVersionMapper::toResponse).toList());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<AppVersionResponse> getAppVersion(UUID appId, UUID versionId) {
        AppVersion version = appVersionService.getVersion(appId, versionId);
        return ResponseEntity.ok(appVersionMapper.toResponse(version));
    }

    @Override
    public ResponseEntity<AppResponse> rollbackAppVersion(UUID appId, UUID versionId) {
        App app = appVersionService.rollback(appId, versionId);
        return ResponseEntity.ok(appMapper.toResponse(app));
    }

    @Override
    public ResponseEntity<VersionSuggestionResponse> suggestNextVersion(UUID appId) {
        String suggested = appVersionService.suggestNextVersion(appId);
        VersionSuggestionResponse response = new VersionSuggestionResponse();
        response.setSuggestedVersion(suggested);
        return ResponseEntity.ok(response);
    }
}

package io.webcontify.app.api;

import io.webcontify.app.mapper.AppMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.model.AppStatus;
import io.webcontify.app.service.AppService;
import io.webcontify.generated.api.AppApi;
import io.webcontify.generated.model.*;
import io.webcontify.identity.model.CheckPermissionRequest;
import io.webcontify.identity.security.RequiresPermission;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.shared.security.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppController implements AppApi {

    private static final List<String> APP_ACTIONS = List.of("viewer", "editor", "admin");

    private final AppService appService;
    private final AppMapper appMapper;
    private final PermissionService permissionService;

    public AppController(AppService appService, AppMapper appMapper, PermissionService permissionService) {
        this.appService = appService;
        this.appMapper = appMapper;
        this.permissionService = permissionService;
    }

    @Override
    @RequiresPermission(action = "editor", resource = "app_management")
    public ResponseEntity<AppResponse> createApp(CreateAppRequest createAppRequest) {
        App app = appMapper.toDomain(createAppRequest);
        App created = appService.create(app);
        AppResponse response = toResponseWithPermissions(created);
        return ResponseEntity.created(URI.create("/api/v1/apps/" + created.getId())).body(response);
    }

    @Override
    // Intentionally no @RequiresPermission: the service filters results based on
    // tenant-wide viewer OR per-app viewer access via OpenFGA listObjects.
    public ResponseEntity<AppListResponse> listApps(
            Integer page, Integer size, @Nullable io.webcontify.generated.model.AppStatus status) {
        AppStatus domainStatus = status != null ? AppStatus.valueOf(status.getValue()) : null;
        Page<App> result = appService.list(PageRequest.of(page, size), domainStatus);

        AppListResponse response = new AppListResponse();
        response.setItems(result.getContent().stream().map(this::toResponseWithPermissions).toList());

        AppListResponsePageInfo pageInfo = new AppListResponsePageInfo();
        pageInfo.setPage(result.getNumber());
        pageInfo.setSize(result.getSize());
        pageInfo.setTotalElements(result.getTotalElements());
        pageInfo.setTotalPages(result.getTotalPages());
        response.setPageInfo(pageInfo);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<AppResponse> getApp(String appId) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("viewer", app.getId());
        return ResponseEntity.ok(toResponseWithPermissions(app));
    }

    @Override
    public ResponseEntity<AppResponse> updateApp(String appId, UpdateAppRequest updateAppRequest) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("editor", app.getId());
        App update = appMapper.toDomain(updateAppRequest);
        App updated = appService.update(app.getId(), update);
        return ResponseEntity.ok(toResponseWithPermissions(updated));
    }

    @Override
    public ResponseEntity<AppResponse> updateAppStatus(
            String appId, UpdateAppStatusRequest updateAppStatusRequest) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("admin", app.getId());
        AppStatus status = AppStatus.valueOf(updateAppStatusRequest.getStatus().getValue());
        App updated = appService.updateStatus(app.getId(), status);
        return ResponseEntity.ok(toResponseWithPermissions(updated));
    }

    @Override
    public ResponseEntity<Void> deleteApp(String appId) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("admin", app.getId());
        appService.delete(app.getId());
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequiresPermission(action = "viewer", resource = "app_management")
    public ResponseEntity<CheckNewAppNameAvailability200Response> checkNewAppNameAvailability(
            String name) {
        CheckNewAppNameAvailability200Response response =
                new CheckNewAppNameAvailability200Response(appService.isNameAvailable(name));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<CheckNewAppNameAvailability200Response> checkAppNameAvailability(
            String appId, String name) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("viewer", app.getId());
        CheckNewAppNameAvailability200Response response =
                new CheckNewAppNameAvailability200Response(appService.isNameAvailable(name));
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

    private AppResponse toResponseWithPermissions(App app) {
        AppResponse response = appMapper.toResponse(app);
        List<String> effectiveActions = permissionService.getEffectiveActions("app", app.getId(), APP_ACTIONS);
        response.setEffectiveActions(effectiveActions.stream()
                .map(AppResponse.EffectiveActionsEnum::fromValue)
                .toList());
        return response;
    }
}

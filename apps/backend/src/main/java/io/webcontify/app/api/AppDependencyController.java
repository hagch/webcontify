package io.webcontify.app.api;

import io.webcontify.app.model.App;
import io.webcontify.app.model.AppDependency;
import io.webcontify.app.service.AppDependencyService;
import io.webcontify.app.service.AppService;
import io.webcontify.app.service.AppValidationService;
import io.webcontify.generated.api.AppDependencyApi;
import io.webcontify.generated.model.*;
import io.webcontify.identity.model.CheckPermissionRequest;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.shared.security.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppDependencyController implements AppDependencyApi {

    private final AppDependencyService dependencyService;
    private final AppValidationService validationService;
    private final AppService appService;
    private final PermissionService permissionService;

    public AppDependencyController(
            AppDependencyService dependencyService,
            AppValidationService validationService,
            AppService appService,
            PermissionService permissionService) {
        this.dependencyService = dependencyService;
        this.validationService = validationService;
        this.appService = appService;
        this.permissionService = permissionService;
    }

    @Override
    public ResponseEntity<AppDependencyResponse> addAppDependency(
            String appId, AddDependencyRequest addDependencyRequest) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("editor", app.getId());
        AppDependency dependency =
                dependencyService.addDependency(app.getId(), addDependencyRequest.getDependsOnAppId());
        AppDependencyResponse response = toResponse(dependency);
        return ResponseEntity.created(
                        URI.create("/api/v1/apps/" + app.getId() + "/dependencies/" + dependency.getDependsOnId()))
                .body(response);
    }

    @Override
    public ResponseEntity<Void> removeAppDependency(String appId, UUID depAppId) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("admin", app.getId());
        dependencyService.removeDependency(app.getId(), depAppId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<AppDependencyListResponse> listAppDependencies(String appId) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("viewer", app.getId());
        List<AppDependency> dependencies = dependencyService.listDependencies(app.getId());
        AppDependencyListResponse response = new AppDependencyListResponse();
        response.setItems(dependencies.stream().map(this::toResponse).toList());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<DependencyGraphResponse> getAppDependencyGraph(String appId) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("viewer", app.getId());
        DependencyGraphResponse response = dependencyService.getDependencyGraph(app.getId());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<DependencyImpactResponse> getAppDependencyImpact(
            String appId, UUID depAppId) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("viewer", app.getId());
        DependencyImpactResponse response = dependencyService.getImpact(app.getId(), depAppId);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<AppProblemsResponse> listAppProblems(String appId) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("viewer", app.getId());
        AppProblemsResponse response = validationService.validate(app.getId());
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

    private AppDependencyResponse toResponse(AppDependency dependency) {
        AppDependencyResponse response = new AppDependencyResponse();
        response.setAppId(dependency.getAppId());
        response.setDependsOnAppId(dependency.getDependsOnId());
        response.setDependsOnAppName(dependency.getDependsOnAppName());
        response.setDependsOnAppSlug(dependency.getDependsOnAppSlug());
        response.setDirect(dependency.isDirect());
        return response;
    }
}

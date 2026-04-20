package io.webcontify.app.api;

import io.webcontify.app.model.AppDependency;
import io.webcontify.app.service.AppDependencyService;
import io.webcontify.app.service.AppValidationService;
import io.webcontify.generated.api.AppDependencyApi;
import io.webcontify.generated.model.*;
import io.webcontify.identity.security.RequiresPermission;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppDependencyController implements AppDependencyApi {

    private final AppDependencyService dependencyService;
    private final AppValidationService validationService;

    public AppDependencyController(
            AppDependencyService dependencyService, AppValidationService validationService) {
        this.dependencyService = dependencyService;
        this.validationService = validationService;
    }

    @Override
    @RequiresPermission(action = "editor", resource = "app", resourceId = "#appId")
    public ResponseEntity<AppDependencyResponse> addAppDependency(
            UUID appId, AddDependencyRequest addDependencyRequest) {
        AppDependency dependency =
                dependencyService.addDependency(appId, addDependencyRequest.getDependsOnAppId());
        AppDependencyResponse response = toResponse(dependency);
        return ResponseEntity.created(
                        URI.create("/api/v1/apps/" + appId + "/dependencies/" + dependency.getDependsOnId()))
                .body(response);
    }

    @Override
    @RequiresPermission(action = "admin", resource = "app", resourceId = "#appId")
    public ResponseEntity<Void> removeAppDependency(UUID appId, UUID depAppId) {
        dependencyService.removeDependency(appId, depAppId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequiresPermission(action = "viewer", resource = "app", resourceId = "#appId")
    public ResponseEntity<AppDependencyListResponse> listAppDependencies(UUID appId) {
        List<AppDependency> dependencies = dependencyService.listDependencies(appId);
        AppDependencyListResponse response = new AppDependencyListResponse();
        response.setItems(dependencies.stream().map(this::toResponse).toList());
        return ResponseEntity.ok(response);
    }

    @Override
    @RequiresPermission(action = "viewer", resource = "app", resourceId = "#appId")
    public ResponseEntity<DependencyGraphResponse> getAppDependencyGraph(UUID appId) {
        DependencyGraphResponse response = dependencyService.getDependencyGraph(appId);
        return ResponseEntity.ok(response);
    }

    @Override
    @RequiresPermission(action = "viewer", resource = "app", resourceId = "#appId")
    public ResponseEntity<DependencyImpactResponse> getAppDependencyImpact(
            UUID appId, UUID depAppId) {
        DependencyImpactResponse response = dependencyService.getImpact(appId, depAppId);
        return ResponseEntity.ok(response);
    }

    @Override
    @RequiresPermission(action = "viewer", resource = "app", resourceId = "#appId")
    public ResponseEntity<AppProblemsResponse> listAppProblems(UUID appId) {
        AppProblemsResponse response = validationService.validate(appId);
        return ResponseEntity.ok(response);
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

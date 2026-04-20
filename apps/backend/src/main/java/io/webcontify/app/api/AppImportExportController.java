package io.webcontify.app.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.app.mapper.AppMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.model.AppBlueprint;
import io.webcontify.app.model.AppSnapshot;
import io.webcontify.app.model.ImportPreview;
import io.webcontify.app.service.AppExportService;
import io.webcontify.app.service.AppImportService;
import io.webcontify.app.service.AppService;
import io.webcontify.generated.api.AppImportExportApi;
import io.webcontify.generated.model.*;
import io.webcontify.identity.model.CheckPermissionRequest;
import io.webcontify.identity.security.RequiresPermission;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.shared.security.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppImportExportController implements AppImportExportApi {

    private final AppExportService appExportService;
    private final AppImportService appImportService;
    private final AppMapper appMapper;
    private final ObjectMapper objectMapper;
    private final AppService appService;
    private final PermissionService permissionService;

    public AppImportExportController(
            AppExportService appExportService,
            AppImportService appImportService,
            AppMapper appMapper,
            AppService appService,
            PermissionService permissionService) {
        this.appExportService = appExportService;
        this.appImportService = appImportService;
        this.appMapper = appMapper;
        this.objectMapper = new ObjectMapper();
        this.appService = appService;
        this.permissionService = permissionService;
    }

    @Override
    public ResponseEntity<AppBlueprintResponse> exportAppBlueprint(String appId, UUID versionId) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("admin", app.getId());
        AppBlueprint blueprint = appExportService.exportBlueprint(app.getId(), versionId);
        return ResponseEntity.ok(toBlueprintResponse(blueprint));
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

    @Override
    @RequiresPermission(action = "editor", resource = "app_management")
    public ResponseEntity<ImportPreviewResponse> analyzeImport(
            AppBlueprintResponse appBlueprintResponse) {
        AppBlueprint blueprint = toBlueprintDomain(appBlueprintResponse);
        ImportPreview preview = appImportService.analyzeBlueprint(blueprint);
        return ResponseEntity.ok(toPreviewResponse(preview));
    }

    @Override
    @RequiresPermission(action = "editor", resource = "app_management")
    public ResponseEntity<ImportResultResponse> executeImport(
            ExecuteImportRequest executeImportRequest) {
        AppBlueprint blueprint = toBlueprintDomain(executeImportRequest.getBlueprint());
        List<App> importedApps =
                appImportService.executeImport(blueprint, executeImportRequest.getSlugOverrides());

        ImportResultResponse response = new ImportResultResponse();
        response.setImportedApps(importedApps.stream().map(appMapper::toResponse).toList());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private AppBlueprintResponse toBlueprintResponse(AppBlueprint blueprint) {
        AppBlueprintResponse response = new AppBlueprintResponse();
        response.setExportedAt(blueprint.exportedAt());
        response.setApps(blueprint.apps().stream().map(this::toBlueprintAppResponse).toList());
        return response;
    }

    private BlueprintApp toBlueprintAppResponse(AppBlueprint.BlueprintApp app) {
        BlueprintApp response = new BlueprintApp();
        response.setSlug(app.slug());
        response.setName(app.name());
        response.setDescription(app.description());
        response.setVersion(app.version());
        response.setDependencies(app.dependencies());
        response.setSnapshot(objectMapper.convertValue(app.snapshot(), Object.class));
        return response;
    }

    private AppBlueprint toBlueprintDomain(AppBlueprintResponse response) {
        List<AppBlueprint.BlueprintApp> apps =
                response.getApps().stream().map(this::toBlueprintAppDomain).toList();
        return new AppBlueprint(response.getExportedAt(), null, apps);
    }

    private AppBlueprint.BlueprintApp toBlueprintAppDomain(BlueprintApp app) {
        AppSnapshot snapshot = objectMapper.convertValue(app.getSnapshot(), AppSnapshot.class);
        return new AppBlueprint.BlueprintApp(
                app.getSlug(),
                app.getName(),
                app.getDescription(),
                app.getVersion(),
                app.getDependencies() != null ? app.getDependencies() : List.of(),
                snapshot);
    }

    private ImportPreviewResponse toPreviewResponse(ImportPreview preview) {
        ImportPreviewResponse response = new ImportPreviewResponse();
        response.setCanImport(preview.canImport());
        response.setWarnings(preview.warnings());
        response.setApps(preview.apps().stream().map(this::toPreviewAppResponse).toList());
        return response;
    }

    private ImportPreviewApp toPreviewAppResponse(ImportPreview.ImportPreviewApp app) {
        ImportPreviewApp response = new ImportPreviewApp();
        response.setOriginalSlug(app.slug());
        response.setOriginalName(app.name());
        response.setHasSlugCollision(app.hasSlugCollision());
        response.setSuggestedSlug(app.suggestedSlug());
        response.setSuggestedName(app.name());
        return response;
    }
}

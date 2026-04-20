package io.webcontify.app.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.app.mapper.AppMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.model.AppBlueprint;
import io.webcontify.app.model.AppSnapshot;
import io.webcontify.app.model.ImportPreview;
import io.webcontify.app.service.AppExportService;
import io.webcontify.app.service.AppImportService;
import io.webcontify.generated.api.AppImportExportApi;
import io.webcontify.generated.model.*;
import io.webcontify.identity.security.RequiresPermission;
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

    public AppImportExportController(
            AppExportService appExportService,
            AppImportService appImportService,
            AppMapper appMapper) {
        this.appExportService = appExportService;
        this.appImportService = appImportService;
        this.appMapper = appMapper;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @RequiresPermission(action = "admin", resource = "app", resourceId = "#appId")
    public ResponseEntity<AppBlueprintResponse> exportAppBlueprint(UUID appId, UUID versionId) {
        AppBlueprint blueprint = appExportService.exportBlueprint(appId, versionId);
        return ResponseEntity.ok(toBlueprintResponse(blueprint));
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

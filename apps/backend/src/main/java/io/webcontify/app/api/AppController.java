package io.webcontify.app.api;

import io.webcontify.app.mapper.AppMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.model.AppStatus;
import io.webcontify.app.service.AppService;
import io.webcontify.generated.api.AppApi;
import io.webcontify.generated.model.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestController;

// TODO: Add @RequiresPermission annotations when app type is added to OpenFGA (Sub-Plan 4e)
@RestController
public class AppController implements AppApi {

    private final AppService appService;
    private final AppMapper appMapper;

    public AppController(AppService appService, AppMapper appMapper) {
        this.appService = appService;
        this.appMapper = appMapper;
    }

    @Override
    public ResponseEntity<AppResponse> createApp(CreateAppRequest createAppRequest) {
        App app = appMapper.toDomain(createAppRequest);
        App created = appService.create(app);
        AppResponse response = appMapper.toResponse(created);
        return ResponseEntity.created(URI.create("/api/v1/apps/" + created.getId())).body(response);
    }

    @Override
    public ResponseEntity<AppListResponse> listApps(
            Integer page, Integer size, @Nullable io.webcontify.generated.model.AppStatus status) {
        AppStatus domainStatus = status != null ? AppStatus.valueOf(status.getValue()) : null;
        Page<App> result = appService.list(PageRequest.of(page, size), domainStatus);

        AppListResponse response = new AppListResponse();
        response.setItems(result.getContent().stream().map(appMapper::toResponse).toList());

        AppListResponsePageInfo pageInfo = new AppListResponsePageInfo();
        pageInfo.setPage(result.getNumber());
        pageInfo.setSize(result.getSize());
        pageInfo.setTotalElements(result.getTotalElements());
        pageInfo.setTotalPages(result.getTotalPages());
        response.setPageInfo(pageInfo);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<AppResponse> getApp(UUID appId) {
        App app = appService.getById(appId);
        return ResponseEntity.ok(appMapper.toResponse(app));
    }

    @Override
    public ResponseEntity<AppResponse> updateApp(UUID appId, UpdateAppRequest updateAppRequest) {
        App update = appMapper.toDomain(updateAppRequest);
        App updated = appService.update(appId, update);
        return ResponseEntity.ok(appMapper.toResponse(updated));
    }

    @Override
    public ResponseEntity<AppResponse> updateAppStatus(
            UUID appId, UpdateAppStatusRequest updateAppStatusRequest) {
        AppStatus status = AppStatus.valueOf(updateAppStatusRequest.getStatus().getValue());
        App updated = appService.updateStatus(appId, status);
        return ResponseEntity.ok(appMapper.toResponse(updated));
    }

    @Override
    public ResponseEntity<Void> deleteApp(UUID appId) {
        appService.delete(appId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<CheckNewAppNameAvailability200Response> checkNewAppNameAvailability(
            String name) {
        CheckNewAppNameAvailability200Response response =
                new CheckNewAppNameAvailability200Response(appService.isNameAvailable(name));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<CheckNewAppNameAvailability200Response> checkAppNameAvailability(
            UUID appId, String name) {
        CheckNewAppNameAvailability200Response response =
                new CheckNewAppNameAvailability200Response(appService.isNameAvailable(name));
        return ResponseEntity.ok(response);
    }
}

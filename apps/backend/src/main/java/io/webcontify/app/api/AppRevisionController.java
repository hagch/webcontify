package io.webcontify.app.api;

import io.webcontify.app.mapper.AppMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.service.AppRevisionService;
import io.webcontify.generated.api.AppRevisionApi;
import io.webcontify.generated.model.AppResponse;
import io.webcontify.generated.model.AppRevisionListResponse;
import io.webcontify.generated.model.AppRevisionResponse;
import io.webcontify.identity.security.RequiresPermission;
import io.webcontify.identity.service.UserSummaryEnricher;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppRevisionController implements AppRevisionApi {

    private final AppRevisionService appRevisionService;
    private final AppMapper appMapper;
    private final UserSummaryEnricher userSummaryEnricher;

    public AppRevisionController(
            AppRevisionService appRevisionService,
            AppMapper appMapper,
            UserSummaryEnricher userSummaryEnricher) {
        this.appRevisionService = appRevisionService;
        this.appMapper = appMapper;
        this.userSummaryEnricher = userSummaryEnricher;
    }

    @Override
    @RequiresPermission(action = "viewer", resource = "app", resourceId = "#appId")
    public ResponseEntity<AppRevisionListResponse> listAppRevisions(UUID appId) {
        List<AppRevisionResponse> revisions = appRevisionService.listRevisions(appId);
        enrichUserSummaries(revisions);
        AppRevisionListResponse response = new AppRevisionListResponse();
        response.setItems(revisions);
        return ResponseEntity.ok(response);
    }

    @Override
    @RequiresPermission(action = "viewer", resource = "app", resourceId = "#appId")
    public ResponseEntity<AppRevisionResponse> getAppRevision(UUID appId, UUID revisionId) {
        AppRevisionResponse revision = appRevisionService.getRevision(revisionId);
        enrichUserSummary(revision);
        return ResponseEntity.ok(revision);
    }

    @Override
    @RequiresPermission(action = "editor", resource = "app", resourceId = "#appId")
    public ResponseEntity<AppResponse> revertAppRevision(UUID appId, UUID revisionId) {
        App app = appRevisionService.revertRevision(appId, revisionId);
        return ResponseEntity.ok(appMapper.toResponse(app));
    }

    private void enrichUserSummary(AppRevisionResponse response) {
        if (response.getCreatedBy() != null) {
            response.setCreatedByUser(userSummaryEnricher.resolve(response.getCreatedBy()));
        }
    }

    private void enrichUserSummaries(List<AppRevisionResponse> items) {
        var userIds = items.stream()
                .map(AppRevisionResponse::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        var userMap = userSummaryEnricher.resolveAll(userIds);
        items.forEach(item -> {
            if (item.getCreatedBy() != null) {
                item.setCreatedByUser(userMap.get(item.getCreatedBy()));
            }
        });
    }
}

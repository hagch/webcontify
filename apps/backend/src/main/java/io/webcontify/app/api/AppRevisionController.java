package io.webcontify.app.api;

import io.webcontify.app.mapper.AppMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.service.AppRevisionService;
import io.webcontify.app.service.AppService;
import io.webcontify.generated.api.AppRevisionApi;
import io.webcontify.generated.model.AppResponse;
import io.webcontify.generated.model.AppRevisionListResponse;
import io.webcontify.generated.model.AppRevisionResponse;
import io.webcontify.identity.model.CheckPermissionRequest;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.identity.service.UserSummaryEnricher;
import io.webcontify.shared.security.TenantContext;
import org.springframework.security.access.AccessDeniedException;
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
    private final AppService appService;
    private final PermissionService permissionService;

    public AppRevisionController(
            AppRevisionService appRevisionService,
            AppMapper appMapper,
            UserSummaryEnricher userSummaryEnricher,
            AppService appService,
            PermissionService permissionService) {
        this.appRevisionService = appRevisionService;
        this.appMapper = appMapper;
        this.userSummaryEnricher = userSummaryEnricher;
        this.appService = appService;
        this.permissionService = permissionService;
    }

    @Override
    public ResponseEntity<AppRevisionListResponse> listAppRevisions(String appId) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("viewer", app.getId());
        List<AppRevisionResponse> revisions = appRevisionService.listRevisions(app.getId());
        enrichUserSummaries(revisions);
        AppRevisionListResponse response = new AppRevisionListResponse();
        response.setItems(revisions);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<AppRevisionResponse> getAppRevision(String appId, UUID revisionId) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("viewer", app.getId());
        AppRevisionResponse revision = appRevisionService.getRevision(revisionId);
        enrichUserSummary(revision);
        return ResponseEntity.ok(revision);
    }

    @Override
    public ResponseEntity<AppResponse> revertAppRevision(String appId, UUID revisionId) {
        App app = appService.getBySlugOrId(appId);
        checkAppPermission("editor", app.getId());
        App reverted = appRevisionService.revertRevision(app.getId(), revisionId);
        return ResponseEntity.ok(appMapper.toResponse(reverted));
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

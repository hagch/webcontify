package io.webcontify.app.api;

import io.webcontify.app.mapper.AppMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.service.AppRevisionService;
import io.webcontify.generated.api.AppRevisionApi;
import io.webcontify.generated.model.AppResponse;
import io.webcontify.generated.model.AppRevisionListResponse;
import io.webcontify.generated.model.AppRevisionResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

// TODO: Add @RequiresPermission when app type is added to OpenFGA (Sub-Plan 4e)
@RestController
public class AppRevisionController implements AppRevisionApi {

    private final AppRevisionService appRevisionService;
    private final AppMapper appMapper;

    public AppRevisionController(AppRevisionService appRevisionService, AppMapper appMapper) {
        this.appRevisionService = appRevisionService;
        this.appMapper = appMapper;
    }

    @Override
    public ResponseEntity<AppRevisionListResponse> listAppRevisions(UUID appId) {
        List<AppRevisionResponse> revisions = appRevisionService.listRevisions(appId);
        AppRevisionListResponse response = new AppRevisionListResponse();
        response.setItems(revisions);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<AppRevisionResponse> getAppRevision(UUID appId, UUID revisionId) {
        AppRevisionResponse revision = appRevisionService.getRevision(revisionId);
        return ResponseEntity.ok(revision);
    }

    @Override
    public ResponseEntity<AppResponse> revertAppRevision(UUID appId, UUID revisionId) {
        App app = appRevisionService.revertRevision(appId, revisionId);
        return ResponseEntity.ok(appMapper.toResponse(app));
    }
}

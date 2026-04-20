package io.webcontify.content.definition.api;

import io.webcontify.content.definition.mapper.ContentDefinitionMapper;
import io.webcontify.content.definition.mapper.ContentFieldMapper;
import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.UndoRedoResult;
import io.webcontify.content.definition.service.ContentDefinitionService;
import io.webcontify.content.definition.service.ContentFieldService;
import io.webcontify.generated.api.ContentDefinitionApi;
import io.webcontify.generated.model.*;
import io.webcontify.identity.security.RequiresPermission;
import io.webcontify.identity.service.PermissionService;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContentDefinitionController implements ContentDefinitionApi {

    private static final List<String> DEFINITION_ACTIONS = List.of("viewer", "editor", "deleter");

    private final ContentDefinitionService definitionService;
    private final ContentFieldService fieldService;
    private final ContentDefinitionMapper definitionMapper;
    private final ContentFieldMapper fieldMapper;
    private final PermissionService permissionService;

    public ContentDefinitionController(
            ContentDefinitionService definitionService,
            ContentFieldService fieldService,
            ContentDefinitionMapper definitionMapper,
            ContentFieldMapper fieldMapper,
            PermissionService permissionService) {
        this.definitionService = definitionService;
        this.fieldService = fieldService;
        this.definitionMapper = definitionMapper;
        this.fieldMapper = fieldMapper;
        this.permissionService = permissionService;
    }

    @Override
    // Per-app permission check in service.
    public ResponseEntity<ContentDefinitionResponse> createContentDefinition(
            String appSlug, CreateContentDefinitionRequest createContentDefinitionRequest) {
        ContentDefinition domain = definitionMapper.toDomain(createContentDefinitionRequest);
        ContentDefinition created = definitionService.create(appSlug, domain);
        ContentDefinitionResponse response = toResponseWithPermissions(created);
        return ResponseEntity.created(
                        URI.create("/api/v1/content/" + appSlug + "/definitions/" + created.getId()))
                .body(response);
    }

    @Override
    // Per-app permission check happens inside the service (viewer on the resolved app).
    public ResponseEntity<ContentDefinitionListResponse> listContentDefinitions(String appSlug) {
        var result = definitionService.listByApp(appSlug);
        ContentDefinitionListResponse response = new ContentDefinitionListResponse();
        response.setItems(result.items().stream().map(this::toResponseWithPermissions).toList());
        response.setReferencedDefinitions(
                result.referencedDefinitions().stream()
                        .map(this::toReferencedDefinitionResponse)
                        .toList());
        return ResponseEntity.ok(response);
    }

    /**
     * Maps a {@code ReferencedDefinitionInfo} to the wire DTO. When the info is
     * inaccessible, only id + accessible=false are populated — no name, no app
     * metadata. This is a security-critical invariant covered by a unit test.
     */
    private ReferencedDefinition toReferencedDefinitionResponse(
            io.webcontify.content.definition.model.ContentDefinitionListResult.ReferencedDefinitionInfo info) {
        ReferencedDefinition dto = new ReferencedDefinition();
        dto.setId(info.id());
        dto.setAccessible(info.accessible());
        if (info.accessible()) {
            dto.setName(info.name());
            dto.setAppId(info.appId());
            dto.setAppName(info.appName());
        }
        return dto;
    }

    @Override
    @RequiresPermission(action = "viewer", resource = "content_definition", resourceId = "#definitionId")
    public ResponseEntity<ContentDefinitionResponse> getContentDefinition(
            String appSlug, UUID definitionId) {
        ContentDefinition definition = definitionService.getById(definitionId);
        ContentDefinitionResponse response = toResponseWithPermissions(definition);
        return ResponseEntity.ok(response);
    }

    @Override
    @RequiresPermission(action = "editor", resource = "content_definition", resourceId = "#definitionId")
    public ResponseEntity<ContentDefinitionResponse> updateContentDefinition(
            String appSlug,
            UUID definitionId,
            UpdateContentDefinitionRequest updateContentDefinitionRequest) {
        ContentDefinition update = definitionMapper.toDomain(updateContentDefinitionRequest);
        ContentDefinition updated = definitionService.update(definitionId, update);
        ContentDefinitionResponse response = toResponseWithPermissions(updated);
        return ResponseEntity.ok(response);
    }

    @Override
    @RequiresPermission(action = "deleter", resource = "content_definition", resourceId = "#definitionId")
    public ResponseEntity<Void> deleteContentDefinition(String appSlug, UUID definitionId) {
        definitionService.delete(definitionId);
        return ResponseEntity.noContent().build();
    }

    @Override
    // Per-app permission check in service.
    public ResponseEntity<CheckContentDefinitionNameAvailability200Response>
            checkContentDefinitionNameAvailability(
                    String appSlug, String name, @Nullable UUID excludeId) {
        boolean available = definitionService.isNameAvailable(appSlug, name);
        CheckContentDefinitionNameAvailability200Response response =
                new CheckContentDefinitionNameAvailability200Response(available);
        return ResponseEntity.ok(response);
    }

    @Override
    // Per-app permission check in service.
    public ResponseEntity<Void> batchUpdatePositions(
            String appSlug, UpdatePositionsRequest updatePositionsRequest) {
        List<ContentDefinition> positions =
                updatePositionsRequest.getPositions().stream()
                        .map(p -> {
                            ContentDefinition cd = new ContentDefinition();
                            cd.setId(p.getDefinitionId());
                            cd.setPositionX(p.getPositionX());
                            cd.setPositionY(p.getPositionY());
                            return cd;
                        })
                        .toList();
        definitionService.updatePositions(positions);
        return ResponseEntity.noContent().build();
    }

    @Override
    // Per-app permission check in service.
    public ResponseEntity<UndoRedoResponse> undoContentChange(String appSlug) {
        UndoRedoResult result = definitionService.undo(appSlug);
        return ResponseEntity.ok(toUndoRedoResponse(result));
    }

    @Override
    // Per-app permission check in service.
    public ResponseEntity<UndoRedoResponse> redoContentChange(String appSlug) {
        UndoRedoResult result = definitionService.redo(appSlug);
        return ResponseEntity.ok(toUndoRedoResponse(result));
    }

    private UndoRedoResponse toUndoRedoResponse(UndoRedoResult result) {
        UndoRedoResponse response = new UndoRedoResponse(result.success());
        response.setEntityType(result.entityType());
        response.setEntityId(result.entityId());
        response.setChangeType(result.changeType());
        response.setCanUndo(result.canUndo());
        response.setCanRedo(result.canRedo());
        return response;
    }

    private ContentDefinitionResponse toResponseWithPermissions(ContentDefinition definition) {
        ContentDefinitionResponse response = definitionMapper.toResponse(definition);
        if (definition.getFields() != null) {
            response.setFields(fieldMapper.toResponseList(definition.getFields()));
        }
        List<String> effectiveActions =
                permissionService.getEffectiveActions(
                        "content_definition", definition.getId(), DEFINITION_ACTIONS);
        response.setEffectiveActions(
                effectiveActions.stream()
                        .map(ContentDefinitionResponse.EffectiveActionsEnum::fromValue)
                        .toList());
        return response;
    }
}

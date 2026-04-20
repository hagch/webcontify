package io.webcontify.content.definition.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.DiffFlags;
import com.flipkart.zjsonpatch.JsonDiff;
import io.webcontify.app.repository.AppRepository;
import io.webcontify.app.repository.entity.AppEntity;
import io.webcontify.content.definition.model.ContentDefinitionListResult;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.model.ReferenceFieldConfig;
import io.webcontify.content.definition.model.UndoRedoResult;
import io.webcontify.content.definition.event.ContentDefinitionChangedEvent;
import io.webcontify.content.definition.event.ContentDefinitionDeletedEvent;
import io.webcontify.content.definition.mapper.ContentDefinitionMapper;
import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.repository.ContentDefinitionRepository;
import io.webcontify.content.definition.repository.ContentFieldRepository;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import io.webcontify.identity.model.CheckPermissionRequest;
import io.webcontify.identity.model.ListPermittedObjectsRequest;
import io.webcontify.identity.model.RelationshipTuple;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.shared.changelog.model.ChangeRecord;
import io.webcontify.shared.changelog.service.ChangeRecordService;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.security.TenantContext;
import io.webcontify.shared.util.SlugGenerator;
import io.webcontify.shared.util.UuidGenerator;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ContentDefinitionService {

    private final ContentDefinitionRepository definitionRepository;
    private final ContentFieldRepository fieldRepository;
    private final AppRepository appRepository;
    private final ContentDefinitionMapper definitionMapper;
    private final SlugGenerator slugGenerator;
    private final ChangeRecordService changeRecordService;
    private final PermissionService permissionService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ContentDefinitionService(
            ContentDefinitionRepository definitionRepository,
            ContentFieldRepository fieldRepository,
            AppRepository appRepository,
            ContentDefinitionMapper definitionMapper,
            SlugGenerator slugGenerator,
            ChangeRecordService changeRecordService,
            PermissionService permissionService,
            ApplicationEventPublisher eventPublisher) {
        this.definitionRepository = definitionRepository;
        this.fieldRepository = fieldRepository;
        this.appRepository = appRepository;
        this.definitionMapper = definitionMapper;
        this.slugGenerator = slugGenerator;
        this.changeRecordService = changeRecordService;
        this.permissionService = permissionService;
        this.eventPublisher = eventPublisher;
    }

    public ContentDefinition create(String appSlug, ContentDefinition definition) {
        var tenantInfo = TenantContext.getTenantInformation();
        UUID tenantId = tenantInfo.tenantId();
        AppEntity app = resolveApp(appSlug);
        requireAppEditor(app.getId());
        UUID appId = app.getId();

        checkNameUniqueness(definition.getName(), appId);

        String slug = slugGenerator.generateUnique(definition.getName(),
                s -> definitionRepository.existsBySlugAndAppIdAndAuditInfoDeletedAtIsNull(s, appId));

        definition.setId(UuidGenerator.generate());
        definition.setTenantId(tenantId);
        definition.setAppId(appId);
        definition.setSlug(slug);

        ContentDefinitionEntity entity = definitionMapper.toEntity(definition);
        entity.setTenantId(tenantId);
        entity = definitionRepository.save(entity);
        ContentDefinition created = definitionMapper.toDomain(entity);

        permissionService.grantRelation(new RelationshipTuple(
                "app:" + appId,
                "parent",
                "content_definition:" + created.getId()));

        eventPublisher.publishEvent(new ContentDefinitionChangedEvent(appId, tenantId));

        return created;
    }

    @Transactional(readOnly = true)
    public ContentDefinition getById(UUID id) {
        ContentDefinitionEntity entity = definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("content_definition.not_found"));
        return definitionMapper.toDomain(entity);
    }

    @Transactional(readOnly = true)
    public ContentDefinitionListResult listByApp(String appSlug) {
        AppEntity app = resolveApp(appSlug);
        var tenantInfo = TenantContext.getTenantInformation();
        UUID userId = TenantContext.getCurrentUserId();

        // Fast-path: platform admin or user with viewer:app sees all definitions.
        // Fallback: user without app-level viewer may still have per-definition
        // viewer tuples (override mode) — filter by listObjects result.
        boolean appViewer = tenantInfo.platformAdmin()
                || permissionService.check(new CheckPermissionRequest(
                        userId, "viewer", "app", app.getId()));

        List<ContentDefinitionEntity> entities;
        if (appViewer) {
            entities = definitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(app.getId());
        } else {
            List<UUID> accessibleIds = permissionService.listObjects(
                    new ListPermittedObjectsRequest(userId, "viewer", "content_definition"));
            if (accessibleIds.isEmpty()) {
                return new ContentDefinitionListResult(List.of(), List.of());
            }
            entities = definitionRepository.findByIdInAndAppIdAndAuditInfoDeletedAtIsNull(
                    accessibleIds, app.getId());
        }

        List<ContentDefinition> items = definitionMapper.toDomainList(entities);
        List<ContentDefinitionListResult.ReferencedDefinitionInfo> refs =
                collectReferencedDefinitions(items, tenantInfo.platformAdmin(), userId);
        return new ContentDefinitionListResult(items, refs);
    }

    /**
     * Collect minimal metadata for REFERENCE targets referenced by visible items
     * but not in the visible set. Inaccessible entries get only id + false;
     * accessible entries get name + appId + appName. Never populates metadata
     * for inaccessible targets.
     */
    private List<ContentDefinitionListResult.ReferencedDefinitionInfo> collectReferencedDefinitions(
            List<ContentDefinition> items, boolean platformAdmin, UUID userId) {
        Set<UUID> visibleIds = items.stream()
                .map(ContentDefinition::getId)
                .collect(java.util.stream.Collectors.toSet());

        Set<UUID> externalTargets = new LinkedHashSet<>();
        for (var def : items) {
            if (def.getFields() == null) continue;
            for (var field : def.getFields()) {
                if (field.getFieldType() == FieldType.REFERENCE
                        && field.getConfig() instanceof ReferenceFieldConfig refConfig
                        && refConfig.targetDefinitionId() != null
                        && !visibleIds.contains(refConfig.targetDefinitionId())) {
                    externalTargets.add(refConfig.targetDefinitionId());
                }
            }
        }

        if (externalTargets.isEmpty()) return List.of();

        var result = new ArrayList<ContentDefinitionListResult.ReferencedDefinitionInfo>();
        for (UUID targetId : externalTargets) {
            boolean accessible = platformAdmin
                    || permissionService.check(new CheckPermissionRequest(
                            userId, "viewer", "content_definition", targetId));
            if (!accessible) {
                result.add(ContentDefinitionListResult.ReferencedDefinitionInfo.inaccessible(targetId));
                continue;
            }
            var targetOpt = definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(targetId);
            if (targetOpt.isEmpty()) {
                // Target has been deleted but a REFERENCE still points to it.
                // Treat as inaccessible to avoid leaking stale metadata.
                result.add(ContentDefinitionListResult.ReferencedDefinitionInfo.inaccessible(targetId));
                continue;
            }
            var target = targetOpt.get();
            var appOpt = appRepository.findByIdAndAuditInfoDeletedAtIsNull(target.getAppId());
            String appName = appOpt.map(AppEntity::getName).orElse(null);
            result.add(ContentDefinitionListResult.ReferencedDefinitionInfo.accessible(
                    targetId, target.getName(), target.getAppId(), appName));
        }
        return result;
    }

    public ContentDefinition update(UUID id, ContentDefinition update) {
        ContentDefinitionEntity entity = definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("content_definition.not_found"));
        var tenantInfo = TenantContext.getTenantInformation();

        if (update.getName() != null && !update.getName().equals(entity.getName())) {
            checkNameUniquenessForUpdate(update.getName(), entity.getAppId(), id);
        }

        JsonNode beforeNode = serializeState(entity);
        definitionMapper.updateEntity(entity, update);
        entity = definitionRepository.save(entity);
        JsonNode afterNode = serializeState(entity);
        createRevisionIfChanged(id, beforeNode, afterNode);

        eventPublisher.publishEvent(new ContentDefinitionChangedEvent(entity.getAppId(), tenantInfo.tenantId()));

        return definitionMapper.toDomain(entity);
    }

    public void delete(UUID id) {
        ContentDefinitionEntity entity = definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("content_definition.not_found"));

        var tenantInfo = TenantContext.getTenantInformation();
        var deletedAt = OffsetDateTime.now();

        // ---------------------------------------------------------------
        // Cascade A: incoming REFERENCE fields + their MIRRORs.
        // Other definitions hold REFERENCE fields that point AT this one;
        // each such field plus any MIRROR that mirrors it must be cleaned
        // up, otherwise the frontend graph shows a ghost placeholder for
        // the deleted target.
        // ---------------------------------------------------------------
        String targetJson = "{\"targetDefinitionId\":\"" + id + "\"}";
        List<ContentFieldEntity> incomingReferences =
                fieldRepository.findReferenceFieldsByTargetDefinitionId(targetJson);
        for (ContentFieldEntity refField : incomingReferences) {
            softDeleteMirrorsOfReference(refField.getId(), deletedAt);
            refField.getAuditInfo().setDeletedAt(deletedAt);
            fieldRepository.save(refField);
        }

        // ---------------------------------------------------------------
        // Cascade B: this definition's OWN REFERENCE fields and their
        // MIRRORs on other definitions. When we delete ourselves, our own
        // REFERENCEs become unreachable — any MIRROR that mirrored them on
        // another definition is now orphaned and must be soft-deleted too.
        // The fields on this definition are themselves implicitly hidden
        // because the parent definition is soft-deleted (all field
        // queries filter by definition + deletedAt), but we soft-delete
        // them explicitly for consistency and so future audit queries see
        // a clean state.
        // ---------------------------------------------------------------
        List<ContentFieldEntity> ownFields =
                fieldRepository.findByDefinitionIdAndAuditInfoDeletedAtIsNullOrderByPositionAsc(id);
        for (ContentFieldEntity ownField : ownFields) {
            if ("REFERENCE".equals(ownField.getFieldType())) {
                softDeleteMirrorsOfReference(ownField.getId(), deletedAt);
            }
            ownField.getAuditInfo().setDeletedAt(deletedAt);
            fieldRepository.save(ownField);
        }

        permissionService.revokeRelation(new RelationshipTuple(
                "app:" + entity.getAppId(),
                "parent",
                "content_definition:" + entity.getId()));

        entity.getAuditInfo().setDeletedAt(deletedAt);
        definitionRepository.save(entity);

        eventPublisher.publishEvent(new ContentDefinitionDeletedEvent(
                entity.getAppId(), tenantInfo.tenantId(), entity.getId()));
    }

    private void softDeleteMirrorsOfReference(UUID referenceFieldId, OffsetDateTime at) {
        String mirrorJson = "{\"sourceFieldId\":\"" + referenceFieldId + "\"}";
        for (ContentFieldEntity mirror : fieldRepository.findMirrorFieldsBySourceFieldId(mirrorJson)) {
            mirror.getAuditInfo().setDeletedAt(at);
            fieldRepository.save(mirror);
        }
    }

    @Transactional(readOnly = true)
    public boolean isNameAvailable(String appSlug, String name) {
        AppEntity app = resolveApp(appSlug);
        requireAppViewer(app.getId());
        return !definitionRepository.existsByNameAndAppIdAndAuditInfoDeletedAtIsNull(name, app.getId());
    }

    public UndoRedoResult undo(String appSlug) {
        AppEntity app = resolveApp(appSlug);
        requireAppEditor(app.getId());
        List<UUID> definitionIds = getDefinitionIds(app.getId());
        List<ChangeRecord> records = changeRecordService.getDraftRecordsForEntities("content_definition", definitionIds);

        // Find the last non-undone record (most recent first, so first match)
        ChangeRecord target = records.stream()
                .filter(r -> !r.getChangeType().startsWith("UNDONE:"))
                .findFirst()
                .orElse(null);

        if (target == null) {
            throw new NotFoundException("undo.nothing_to_undo");
        }

        // Mark as undone by prefixing changeType
        changeRecordService.updateChangeType(target.getId(), "UNDONE:" + target.getChangeType());

        // Compute canUndo/canRedo after the operation
        boolean canUndo = records.stream()
                .filter(r -> !r.getId().equals(target.getId()))
                .anyMatch(r -> !r.getChangeType().startsWith("UNDONE:"));
        boolean canRedo = true; // We just undid something, so redo is always possible

        return new UndoRedoResult(true, target.getEntityType(), target.getEntityId(),
                target.getChangeType(), canUndo, canRedo);
    }

    public UndoRedoResult redo(String appSlug) {
        AppEntity app = resolveApp(appSlug);
        requireAppEditor(app.getId());
        List<UUID> definitionIds = getDefinitionIds(app.getId());
        List<ChangeRecord> records = changeRecordService.getDraftRecordsForEntities("content_definition", definitionIds);

        // Find the most recently undone record (most recent first, so first match)
        ChangeRecord target = records.stream()
                .filter(r -> r.getChangeType().startsWith("UNDONE:"))
                .findFirst()
                .orElse(null);

        if (target == null) {
            throw new NotFoundException("redo.nothing_to_redo");
        }

        // Restore original changeType
        String originalType = target.getChangeType().substring("UNDONE:".length());
        changeRecordService.updateChangeType(target.getId(), originalType);

        // Compute canUndo/canRedo after the operation
        boolean canUndo = true; // We just redid something, so undo is always possible
        boolean canRedo = records.stream()
                .filter(r -> !r.getId().equals(target.getId()))
                .anyMatch(r -> r.getChangeType().startsWith("UNDONE:"));

        return new UndoRedoResult(true, target.getEntityType(), target.getEntityId(),
                originalType, canUndo, canRedo);
    }

    private List<UUID> getDefinitionIds(UUID appId) {
        return definitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(appId)
                .stream()
                .map(ContentDefinitionEntity::getId)
                .toList();
    }

    public void updatePositions(List<ContentDefinition> positions) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        // Resolve app from the first definition to perform per-app permission check.
        ContentDefinitionEntity first = definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(positions.get(0).getId())
                .orElseThrow(() -> new NotFoundException("content_definition.not_found"));
        AppEntity app = appRepository.findByIdAndAuditInfoDeletedAtIsNull(first.getAppId())
                .orElseThrow(() -> new NotFoundException("app.not_found"));
        requireAppEditor(app.getId());

        for (ContentDefinition position : positions) {
            ContentDefinitionEntity entity = definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(position.getId())
                    .orElseThrow(() -> new NotFoundException("content_definition.not_found"));
            entity.setPositionX(position.getPositionX());
            entity.setPositionY(position.getPositionY());
            definitionRepository.save(entity);
        }
    }

    private void requireAppEditor(UUID appId) {
        var tenantInfo = TenantContext.getTenantInformation();
        if (tenantInfo.platformAdmin()) return;
        boolean allowed = permissionService.check(new CheckPermissionRequest(
                TenantContext.getCurrentUserId(), "editor", "app", appId));
        if (!allowed) {
            throw new AccessDeniedException("Insufficient permissions");
        }
    }

    private void requireAppViewer(UUID appId) {
        var tenantInfo = TenantContext.getTenantInformation();
        if (tenantInfo.platformAdmin()) return;
        boolean allowed = permissionService.check(new CheckPermissionRequest(
                TenantContext.getCurrentUserId(), "viewer", "app", appId));
        if (!allowed) {
            throw new AccessDeniedException("Insufficient permissions");
        }
    }

    private AppEntity resolveApp(String appSlug) {
        return appRepository.findBySlugAndAuditInfoDeletedAtIsNull(appSlug)
                .orElseThrow(() -> new NotFoundException("app.not_found"));
    }

    private void checkNameUniqueness(String name, UUID appId) {
        if (definitionRepository.existsByNameAndAppIdAndAuditInfoDeletedAtIsNull(name, appId)) {
            throw new ConflictException("content_definition.name_already_exists");
        }
    }

    private void checkNameUniquenessForUpdate(String name, UUID appId, UUID excludeId) {
        if (definitionRepository.existsByNameAndAppIdAndIdNotAndAuditInfoDeletedAtIsNull(name, appId, excludeId)) {
            throw new ConflictException("content_definition.name_already_exists");
        }
    }

    private JsonNode serializeState(ContentDefinitionEntity entity) {
        var node = objectMapper.createObjectNode();
        node.put("name", entity.getName());
        node.put("description", entity.getDescription() != null ? entity.getDescription() : "");
        node.put("displayFormat", entity.getDisplayFormat() != null ? entity.getDisplayFormat() : "");
        return node;
    }

    private void createRevisionIfChanged(UUID definitionId, JsonNode before, JsonNode after) {
        JsonNode patch = JsonDiff.asJson(before, after,
                EnumSet.of(DiffFlags.ADD_ORIGINAL_VALUE_ON_REPLACE));
        if (patch.size() > 0) {
            var tenantInfo = TenantContext.getTenantInformation();
            changeRecordService.create(
                    "content_definition", definitionId, "updated",
                    patch.toString(), "user:" + tenantInfo.tenantId(), "draft");
        }
    }
}

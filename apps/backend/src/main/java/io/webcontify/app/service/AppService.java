package io.webcontify.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.DiffFlags;
import com.flipkart.zjsonpatch.JsonDiff;
import io.webcontify.app.mapper.AppMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.model.AppStatus;
import io.webcontify.app.repository.AppDependencyRepository;
import io.webcontify.app.repository.AppRepository;
import io.webcontify.app.repository.AppVersionRepository;
import io.webcontify.app.repository.entity.AppEntity;
import io.webcontify.identity.model.RelationshipTuple;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.shared.changelog.service.ChangeRecordService;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.security.TenantContext;
import io.webcontify.shared.util.SlugGenerator;
import io.webcontify.shared.util.UuidGenerator;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AppService {

    private final AppRepository appRepository;
    private final AppDependencyRepository dependencyRepository;
    private final AppVersionRepository versionRepository;
    private final AppMapper appMapper;
    private final SlugGenerator slugGenerator;
    private final ChangeRecordService changeRecordService;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AppService(
            AppRepository appRepository,
            AppDependencyRepository dependencyRepository,
            AppVersionRepository versionRepository,
            AppMapper appMapper,
            SlugGenerator slugGenerator,
            ChangeRecordService changeRecordService,
            PermissionService permissionService) {
        this.appRepository = appRepository;
        this.dependencyRepository = dependencyRepository;
        this.versionRepository = versionRepository;
        this.appMapper = appMapper;
        this.slugGenerator = slugGenerator;
        this.changeRecordService = changeRecordService;
        this.permissionService = permissionService;
    }

    public App create(App app) {
        var tenantInfo = TenantContext.getTenantInformation();
        UUID tenantId = tenantInfo.tenantId();
        checkNameUniqueness(app.getName(), tenantId);
        String slug = slugGenerator.generateUnique(app.getName(),
                s -> appRepository.existsBySlugAndTenantIdAndAuditInfoDeletedAtIsNull(s, tenantId));
        app.setId(UuidGenerator.generate());
        app.setTenantId(tenantId);
        app.setSlug(slug);
        app.setStatus(AppStatus.DRAFT);
        app.setHasDraft(false);
        AppEntity entity = appMapper.toEntity(app);
        entity.setTenantId(tenantId);
        entity = appRepository.save(entity);
        App created = appMapper.toDomain(entity);

        permissionService.grantRelation(new RelationshipTuple(
                "app_management:" + tenantId,
                "management",
                "app:" + created.getId()
        ));

        return created;
    }

    @Transactional(readOnly = true)
    public App getById(UUID id) {
        AppEntity entity = appRepository.findByIdAndAuditInfoDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("app.not_found"));
        App app = appMapper.toDomain(entity);
        enrichCurrentVersion(app);
        return app;
    }

    @Transactional(readOnly = true)
    public Page<App> list(Pageable pageable, AppStatus status) {
        Page<AppEntity> entities;
        if (status != null) {
            entities = appRepository.findByStatusAndAuditInfoDeletedAtIsNull(status.name(), pageable);
        } else {
            entities = appRepository.findByAuditInfoDeletedAtIsNull(pageable);
        }
        return entities.map(e -> {
            App app = appMapper.toDomain(e);
            enrichCurrentVersion(app);
            return app;
        });
    }

    public App update(UUID id, App update) {
        AppEntity entity = appRepository.findByIdAndAuditInfoDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("app.not_found"));
        var tenantInfo = TenantContext.getTenantInformation();
        UUID tenantId = tenantInfo.tenantId();
        if (update.getName() != null && !update.getName().equals(entity.getName())) {
            checkNameUniqueness(update.getName(), tenantId);
        }
        if ("ACTIVE".equals(entity.getStatus())) {
            entity.setHasDraft(true);
        }
        JsonNode beforeNode = serializeAppState(entity);
        appMapper.updateEntity(entity, update);
        entity = appRepository.save(entity);
        JsonNode afterNode = serializeAppState(entity);
        createRevisionIfChanged(id, beforeNode, afterNode);
        return appMapper.toDomain(entity);
    }

    public App updateStatus(UUID id, AppStatus status) {
        AppEntity entity = appRepository.findByIdAndAuditInfoDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("app.not_found"));
        entity.setStatus(status.name());
        entity = appRepository.save(entity);
        return appMapper.toDomain(entity);
    }

    public void delete(UUID id) {
        AppEntity entity = appRepository.findByIdAndAuditInfoDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("app.not_found"));

        var dependants = dependencyRepository.findByDependsOnId(id);
        if (!dependants.isEmpty()) {
            throw new ConflictException("app.delete.has_dependants");
        }

        var tenantInfo = TenantContext.getTenantInformation();
        permissionService.revokeRelation(new RelationshipTuple(
                "app_management:" + tenantInfo.tenantId(),
                "management",
                "app:" + entity.getId()
        ));

        entity.getAuditInfo().setDeletedAt(OffsetDateTime.now());
        appRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public boolean isNameAvailable(String name) {
        var tenantInfo = TenantContext.getTenantInformation();
        return !appRepository.existsByNameAndTenantIdAndAuditInfoDeletedAtIsNull(name, tenantInfo.tenantId());
    }

    private void enrichCurrentVersion(App app) {
        versionRepository.findTopByAppIdOrderByPublishedAtDesc(app.getId())
                .ifPresent(v -> app.setCurrentVersion(v.getVersion()));
    }

    private void checkNameUniqueness(String name, UUID tenantId) {
        if (appRepository.existsByNameAndTenantIdAndAuditInfoDeletedAtIsNull(name, tenantId)) {
            throw new ConflictException("app.name_already_exists");
        }
    }

    private JsonNode serializeAppState(AppEntity entity) {
        var node = objectMapper.createObjectNode();
        node.put("name", entity.getName());
        node.put("description", entity.getDescription() != null ? entity.getDescription() : "");
        return node;
    }

    private void createRevisionIfChanged(UUID appId, JsonNode before, JsonNode after) {
        JsonNode patch = JsonDiff.asJson(before, after,
                EnumSet.of(DiffFlags.ADD_ORIGINAL_VALUE_ON_REPLACE));
        if (patch.size() > 0) {
            var tenantInfo = TenantContext.getTenantInformation();
            changeRecordService.create(
                    "app", appId, "updated", patch.toString(), "user:" + tenantInfo.tenantId(), "draft");
        }
    }
}

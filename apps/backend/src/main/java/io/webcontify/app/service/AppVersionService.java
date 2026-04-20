package io.webcontify.app.service;

import io.webcontify.app.event.AppPublishedEvent;
import io.webcontify.app.mapper.AppVersionMapper;
import io.webcontify.app.model.*;
import io.webcontify.app.repository.AppRepository;
import io.webcontify.app.repository.AppVersionRepository;
import io.webcontify.app.repository.entity.AppEntity;
import io.webcontify.app.repository.entity.AppVersionEntity;
import io.webcontify.generated.model.VersionDiffEntry;
import io.webcontify.generated.model.VersionDiffResponse;
import io.webcontify.generated.model.VersionSchemaDefinition;
import io.webcontify.generated.model.VersionSchemaDependency;
import io.webcontify.generated.model.VersionSchemaField;
import io.webcontify.generated.model.VersionSchemaGraphEdge;
import io.webcontify.generated.model.VersionSchemaGraphNode;
import io.webcontify.generated.model.VersionSchemaResponse;
import io.webcontify.shared.changelog.service.ChangeRecordService;
import io.webcontify.shared.exception.BadRequestException;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.security.TenantContext;
import io.webcontify.shared.security.TenantSlugResolver;
import io.webcontify.shared.util.UuidGenerator;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AppVersionService {

    private final AppRepository appRepository;
    private final AppVersionRepository appVersionRepository;
    private final AppVersionMapper appVersionMapper;
    private final AppSnapshotService appSnapshotService;
    private final AppDependencyService dependencyService;
    private final AppService appService;
    private final AppValidationService validationService;
    private final ChangeRecordService changeRecordService;
    private final TenantSlugResolver tenantSlugResolver;
    private final ApplicationEventPublisher eventPublisher;

    public AppVersionService(
            AppRepository appRepository,
            AppVersionRepository appVersionRepository,
            AppVersionMapper appVersionMapper,
            AppSnapshotService appSnapshotService,
            AppDependencyService dependencyService,
            AppService appService,
            AppValidationService validationService,
            ChangeRecordService changeRecordService,
            TenantSlugResolver tenantSlugResolver,
            ApplicationEventPublisher eventPublisher) {
        this.appRepository = appRepository;
        this.appVersionRepository = appVersionRepository;
        this.appVersionMapper = appVersionMapper;
        this.appSnapshotService = appSnapshotService;
        this.dependencyService = dependencyService;
        this.appService = appService;
        this.validationService = validationService;
        this.changeRecordService = changeRecordService;
        this.tenantSlugResolver = tenantSlugResolver;
        this.eventPublisher = eventPublisher;
    }

    public AppVersion publish(UUID appId, String version, String releaseNotes) {
        var tenantInfo = TenantContext.getTenantInformation();
        App app = appService.getById(appId);

        var problems = validationService.validate(appId);
        if (problems.getErrorCount() > 0) {
            throw new BadRequestException("app.publish.has_problems");
        }

        if (appVersionRepository.existsByAppIdAndVersion(appId, version)) {
            throw new ConflictException("app.version.already_exists");
        }

        String tenantSlug = tenantSlugResolver.resolveSlugByTenantId(tenantInfo.tenantId())
                .orElseThrow(() -> new NotFoundException("tenant.not_found"));
        String bucketName = "tenant-" + tenantSlug;

        AppSnapshot snapshot = appSnapshotService.buildSnapshot(app, version);
        String s3Key = appSnapshotService.uploadSnapshot(bucketName, snapshot);

        AppVersionEntity entity = new AppVersionEntity();
        entity.setId(UuidGenerator.generate());
        entity.setAppId(appId);
        entity.setTenantId(tenantInfo.tenantId());
        entity.setVersion(version);
        entity.setS3Key(s3Key);
        entity.setReleaseNotes(releaseNotes);
        entity.setPublishedAt(OffsetDateTime.now());
        entity.setPublishedBy(TenantContext.getCurrentUserId());
        entity = appVersionRepository.save(entity);

        AppEntity appEntity = appRepository.findByIdAndAuditInfoDeletedAtIsNull(appId)
                .orElseThrow(() -> new NotFoundException("app.not_found"));
        appEntity.setStatus(AppStatus.ACTIVE.name());
        appEntity.setHasDraft(false);
        appRepository.save(appEntity);

        changeRecordService.closeDraftRecords("app", appId);

        eventPublisher.publishEvent(new AppPublishedEvent(appId, tenantInfo.tenantId(), version));

        return appVersionMapper.toDomain(entity);
    }

    @Transactional(readOnly = true)
    public List<AppVersion> listVersions(UUID appId) {
        appService.getById(appId);
        return appVersionRepository.findByAppIdOrderByPublishedAtDesc(appId).stream()
                .map(appVersionMapper::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public AppVersion getVersion(UUID appId, UUID versionId) {
        AppVersionEntity entity = appVersionRepository.findByIdAndAppId(versionId, appId)
                .orElseThrow(() -> new NotFoundException("app.version.not_found"));
        return appVersionMapper.toDomain(entity);
    }

    public App rollback(UUID appId, UUID versionId) {
        getVersion(appId, versionId);

        AppEntity appEntity = appRepository.findByIdAndAuditInfoDeletedAtIsNull(appId)
                .orElseThrow(() -> new NotFoundException("app.not_found"));
        appEntity.setHasDraft(true);
        appRepository.save(appEntity);

        var tenantInfo = TenantContext.getTenantInformation();
        changeRecordService.create("app", appId, "ROLLBACK", "{}", "VERSION_ROLLBACK",
                suggestNextVersion(appId));

        return appService.getById(appId);
    }

    @Transactional(readOnly = true)
    public String suggestNextVersion(UUID appId) {
        return appVersionRepository.findTopByAppIdOrderByPublishedAtDesc(appId)
                .map(entity -> incrementPatchVersion(entity.getVersion()))
                .orElse("0.1.0");
    }

    @Transactional(readOnly = true)
    public VersionDiffResponse getVersionDiff(UUID appId, UUID versionId) {
        var tenantInfo = TenantContext.getTenantInformation();
        AppVersionEntity versionEntity = appVersionRepository.findByIdAndAppId(versionId, appId)
                .orElseThrow(() -> new NotFoundException("app.version.not_found"));

        String tenantSlug = tenantSlugResolver.resolveSlugByTenantId(tenantInfo.tenantId())
                .orElseThrow(() -> new NotFoundException("tenant.not_found"));
        String bucketName = "tenant-" + tenantSlug;

        AppSnapshot snapshot = appSnapshotService.downloadSnapshot(bucketName, versionEntity.getS3Key());
        App currentApp = appService.getById(appId);
        var currentDeps = dependencyService.listDependencies(appId).stream()
                .filter(AppDependency::isDirect)
                .collect(Collectors.toList());

        List<VersionDiffEntry> changes = new ArrayList<>();

        // Compare name
        String versionName = snapshot.metadata().name();
        if (!Objects.equals(currentApp.getName(), versionName)) {
            var entry = new VersionDiffEntry();
            entry.setField("name");
            entry.setChangeType(VersionDiffEntry.ChangeTypeEnum.MODIFIED);
            entry.setCurrentValue(currentApp.getName());
            entry.setVersionValue(versionName);
            changes.add(entry);
        }

        // Compare description
        String versionDesc = snapshot.metadata().description();
        String currentDesc = currentApp.getDescription() != null ? currentApp.getDescription() : "";
        String snapshotDesc = versionDesc != null ? versionDesc : "";
        if (!Objects.equals(currentDesc, snapshotDesc)) {
            var entry = new VersionDiffEntry();
            entry.setField("description");
            entry.setChangeType(VersionDiffEntry.ChangeTypeEnum.MODIFIED);
            entry.setCurrentValue(currentDesc);
            entry.setVersionValue(snapshotDesc);
            changes.add(entry);
        }

        // Compare dependencies
        Set<String> currentDepSlugs = currentDeps.stream()
                .map(AppDependency::getDependsOnAppSlug)
                .collect(Collectors.toSet());
        Set<String> versionDepSlugs = snapshot.dependencies().stream()
                .map(AppSnapshot.AppSnapshotDependency::appSlug)
                .collect(Collectors.toSet());

        for (String slug : currentDepSlugs) {
            if (!versionDepSlugs.contains(slug)) {
                var entry = new VersionDiffEntry();
                entry.setField("dependency:" + slug);
                entry.setChangeType(VersionDiffEntry.ChangeTypeEnum.ADDED);
                entry.setCurrentValue(slug);
                changes.add(entry);
            }
        }
        for (String slug : versionDepSlugs) {
            if (!currentDepSlugs.contains(slug)) {
                var entry = new VersionDiffEntry();
                entry.setField("dependency:" + slug);
                entry.setChangeType(VersionDiffEntry.ChangeTypeEnum.REMOVED);
                entry.setVersionValue(slug);
                changes.add(entry);
            }
        }

        var response = new VersionDiffResponse();
        response.setVersionId(versionId);
        response.setVersion(versionEntity.getVersion());
        response.setChanges(changes);
        response.setHasChanges(!changes.isEmpty());
        return response;
    }

    @Transactional(readOnly = true)
    public VersionSchemaResponse getVersionSchema(UUID appId, UUID versionId) {
        var tenantInfo = TenantContext.getTenantInformation();
        AppVersionEntity versionEntity = appVersionRepository.findByIdAndAppId(versionId, appId)
                .orElseThrow(() -> new NotFoundException("app.version.not_found"));

        String tenantSlug = tenantSlugResolver.resolveSlugByTenantId(tenantInfo.tenantId())
                .orElseThrow(() -> new NotFoundException("tenant.not_found"));
        String bucketName = "tenant-" + tenantSlug;

        AppSnapshot snapshot = appSnapshotService.downloadSnapshot(bucketName, versionEntity.getS3Key());

        List<VersionSchemaDefinition> definitions = snapshot.contentDefinitions().stream()
                .map(def -> {
                    var definition = new VersionSchemaDefinition();
                    definition.setId(def.id());
                    definition.setName(def.name());
                    definition.setSlug(def.slug());
                    definition.setDescription(def.description());
                    definition.setDisplayFormat(def.displayFormat());
                    definition.setFields(def.fields().stream()
                            .map(f -> {
                                var field = new VersionSchemaField();
                                field.setId(f.id());
                                field.setFieldKey(f.fieldKey());
                                field.setFieldType(f.fieldType());
                                field.setRequired(f.required());
                                field.setUniqueValue(f.uniqueValue());
                                field.setSortable(f.sortable());
                                field.setFilterable(f.filterable());
                                field.setConfig(f.config());
                                field.setPosition(f.position());
                                return field;
                            })
                            .toList());
                    return definition;
                })
                .toList();

        List<VersionSchemaDependency> dependencies = snapshot.dependencies().stream()
                .map(dep -> {
                    var dependency = new VersionSchemaDependency();
                    dependency.setAppSlug(dep.appSlug());
                    dependency.setVersion(dep.version());
                    return dependency;
                })
                .toList();

        List<VersionSchemaGraphNode> graphNodes = (snapshot.graphNodes() != null
                ? snapshot.graphNodes()
                : List.<AppSnapshot.AppSnapshotGraphNode>of()).stream()
                .map(n -> new VersionSchemaGraphNode()
                        .id(UUID.fromString(n.id()))
                        .accessible(n.accessible())
                        .name(n.name())
                        .slug(n.slug())
                        .status(n.status())
                        .isCurrent(n.isCurrent()))
                .toList();

        List<VersionSchemaGraphEdge> graphEdges = (snapshot.graphEdges() != null
                ? snapshot.graphEdges()
                : List.<AppSnapshot.AppSnapshotGraphEdge>of()).stream()
                .map(e -> new VersionSchemaGraphEdge()
                        .source(UUID.fromString(e.source()))
                        .target(UUID.fromString(e.target()))
                        .direct(e.direct()))
                .toList();

        var response = new VersionSchemaResponse();
        response.setVersionId(versionId);
        response.setVersion(versionEntity.getVersion());
        response.setContentDefinitions(definitions);
        response.setDependencies(dependencies);
        response.setGraphNodes(graphNodes);
        response.setGraphEdges(graphEdges);
        return response;
    }

    private String incrementPatchVersion(String version) {
        String[] parts = version.split("\\.");
        if (parts.length != 3) {
            return version + ".1";
        }
        int patch = Integer.parseInt(parts[2]) + 1;
        return parts[0] + "." + parts[1] + "." + patch;
    }
}

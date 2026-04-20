package io.webcontify.app.service;

import io.webcontify.app.event.AppPublishedEvent;
import io.webcontify.app.mapper.AppVersionMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.model.AppSnapshot;
import io.webcontify.app.model.AppStatus;
import io.webcontify.app.model.AppVersion;
import io.webcontify.app.repository.AppRepository;
import io.webcontify.app.repository.AppVersionRepository;
import io.webcontify.app.repository.entity.AppEntity;
import io.webcontify.app.repository.entity.AppVersionEntity;
import io.webcontify.shared.changelog.service.ChangeRecordService;
import io.webcontify.shared.exception.BadRequestException;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.security.TenantContext;
import io.webcontify.shared.security.TenantSlugResolver;
import io.webcontify.shared.util.UuidGenerator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
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
            AppService appService,
            AppValidationService validationService,
            ChangeRecordService changeRecordService,
            TenantSlugResolver tenantSlugResolver,
            ApplicationEventPublisher eventPublisher) {
        this.appRepository = appRepository;
        this.appVersionRepository = appVersionRepository;
        this.appVersionMapper = appVersionMapper;
        this.appSnapshotService = appSnapshotService;
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
        entity.setPublishedBy(tenantInfo.tenantId());
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

    private String incrementPatchVersion(String version) {
        String[] parts = version.split("\\.");
        if (parts.length != 3) {
            return version + ".1";
        }
        int patch = Integer.parseInt(parts[2]) + 1;
        return parts[0] + "." + parts[1] + "." + patch;
    }
}

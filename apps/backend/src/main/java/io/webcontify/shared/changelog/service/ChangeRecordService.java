package io.webcontify.shared.changelog.service;

import io.webcontify.shared.changelog.model.ChangeRecord;
import io.webcontify.shared.changelog.repository.ChangeRecordRepository;
import io.webcontify.shared.changelog.repository.entity.ChangeRecordEntity;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.security.TenantContext;
import io.webcontify.shared.util.UuidGenerator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ChangeRecordService {

    private final ChangeRecordRepository changeRecordRepository;

    public ChangeRecordService(ChangeRecordRepository changeRecordRepository) {
        this.changeRecordRepository = changeRecordRepository;
    }

    public ChangeRecord create(
            String entityType,
            UUID entityId,
            String changeType,
            String payload,
            String source,
            String draftVersion) {
        var tenantInfo = TenantContext.getTenantInformation();

        ChangeRecordEntity entity = new ChangeRecordEntity();
        entity.setId(UuidGenerator.generate());
        entity.setTenantId(tenantInfo.tenantId());
        entity.setEntityType(entityType);
        entity.setEntityId(entityId);
        entity.setChangeType(changeType);
        entity.setPayload(payload);
        entity.setSource(source);
        entity.setDraftVersion(draftVersion);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setCreatedBy(TenantContext.getCurrentUserId());

        ChangeRecordEntity saved = changeRecordRepository.save(entity);
        return toModel(saved);
    }

    @Transactional(readOnly = true)
    public ChangeRecord getById(UUID id) {
        return changeRecordRepository
                .findById(id)
                .map(this::toModel)
                .orElseThrow(() -> new NotFoundException("change_record.not_found"));
    }

    @Transactional(readOnly = true)
    public List<ChangeRecord> getDraftRecords(String entityType, UUID entityId) {
        return changeRecordRepository
                .findByEntityTypeAndEntityIdAndDraftVersionIsNotNullOrderByCreatedAtDesc(
                        entityType, entityId)
                .stream()
                .map(this::toModel)
                .toList();
    }

    public void closeDraftRecords(String entityType, UUID entityId) {
        changeRecordRepository.closeDraftRecords(entityType, entityId);
    }

    private ChangeRecord toModel(ChangeRecordEntity entity) {
        ChangeRecord model = new ChangeRecord();
        model.setId(entity.getId());
        model.setTenantId(entity.getTenantId());
        model.setEntityType(entity.getEntityType());
        model.setEntityId(entity.getEntityId());
        model.setChangeType(entity.getChangeType());
        model.setPayload(entity.getPayload());
        model.setSource(entity.getSource());
        model.setDraftVersion(entity.getDraftVersion());
        model.setCreatedAt(entity.getCreatedAt());
        model.setCreatedBy(entity.getCreatedBy());
        return model;
    }
}

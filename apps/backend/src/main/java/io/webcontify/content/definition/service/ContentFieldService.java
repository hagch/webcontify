package io.webcontify.content.definition.service;

import io.webcontify.content.definition.event.ContentDefinitionChangedEvent;
import io.webcontify.content.definition.mapper.ContentFieldMapper;
import io.webcontify.content.definition.mapper.FieldConfigConverter;
import io.webcontify.content.definition.model.*;
import io.webcontify.content.definition.repository.ContentDefinitionRepository;
import io.webcontify.content.definition.repository.ContentFieldRepository;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import io.webcontify.shared.changelog.service.ChangeRecordService;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.FieldError;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.exception.ValidationException;
import io.webcontify.shared.security.TenantContext;
import io.webcontify.shared.util.UuidGenerator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ContentFieldService {

    private final ContentFieldRepository fieldRepository;
    private final ContentDefinitionRepository definitionRepository;
    private final ContentFieldMapper fieldMapper;
    private final FieldConfigConverter fieldConfigConverter;
    private final ContentFieldConfigValidator configValidator;
    private final ContentReferenceValidator referenceValidator;
    private final ChangeRecordService changeRecordService;
    private final ApplicationEventPublisher eventPublisher;

    public ContentFieldService(
            ContentFieldRepository fieldRepository,
            ContentDefinitionRepository definitionRepository,
            ContentFieldMapper fieldMapper,
            FieldConfigConverter fieldConfigConverter,
            ContentFieldConfigValidator configValidator,
            ContentReferenceValidator referenceValidator,
            ChangeRecordService changeRecordService,
            ApplicationEventPublisher eventPublisher) {
        this.fieldRepository = fieldRepository;
        this.definitionRepository = definitionRepository;
        this.fieldMapper = fieldMapper;
        this.fieldConfigConverter = fieldConfigConverter;
        this.configValidator = configValidator;
        this.referenceValidator = referenceValidator;
        this.changeRecordService = changeRecordService;
        this.eventPublisher = eventPublisher;
    }

    public FieldDefinition addField(UUID definitionId, FieldDefinition field) {
        var tenantInfo = TenantContext.getTenantInformation();
        ContentDefinitionEntity definition = resolveDefinition(definitionId);

        checkFieldKeyUniqueness(field.getFieldKey(), definitionId);
        configValidator.validate(field.getFieldType(), field.getConfig());

        if (field.getFieldType() == FieldType.REFERENCE && field.getConfig() instanceof ReferenceFieldConfig refConfig) {
            referenceValidator.validateReference(definition.getAppId(), refConfig);
        }

        int position = fieldRepository.countByDefinitionIdAndAuditInfoDeletedAtIsNull(definitionId);
        field.setPosition(position);
        field.setId(UuidGenerator.generate());
        field.setTenantId(tenantInfo.tenantId());
        field.setDefinitionId(definitionId);

        ContentFieldEntity entity = fieldMapper.toEntity(field);
        entity.setTenantId(tenantInfo.tenantId());
        entity.setDefinition(definition);
        entity = fieldRepository.save(entity);

        FieldDefinition created = fieldMapper.toDomain(entity);

        if (field.getFieldType() == FieldType.REFERENCE && field.getConfig() instanceof ReferenceFieldConfig refConfig) {
            createMirrorField(definition, entity, refConfig, tenantInfo.tenantId());
        }

        changeRecordService.create(
                "content_field", created.getId(), "created",
                "{}", "user:" + tenantInfo.tenantId(), "draft");

        eventPublisher.publishEvent(new ContentDefinitionChangedEvent(definition.getAppId(), tenantInfo.tenantId()));

        return created;
    }

    public FieldDefinition updateField(UUID fieldId, FieldDefinition update) {
        var tenantInfo = TenantContext.getTenantInformation();
        ContentFieldEntity existingEntity = resolveField(fieldId);

        FieldType existingType = FieldType.valueOf(existingEntity.getFieldType());
        if (update.getFieldType() != null && update.getFieldType() != existingType) {
            throw new ValidationException("VALIDATION_ERROR",
                    List.of(new FieldError("fieldType", "error.contentField.fieldType.immutable")));
        }

        ContentDefinitionEntity definition = resolveDefinition(existingEntity.getDefinitionId());

        if (update.getFieldKey() != null && !update.getFieldKey().equals(existingEntity.getFieldKey())) {
            checkFieldKeyUniquenessForUpdate(update.getFieldKey(), existingEntity.getDefinitionId(), fieldId);
            existingEntity.setFieldKey(update.getFieldKey());
        }

        if (update.getConfig() != null) {
            configValidator.validate(existingType, update.getConfig());
            existingEntity.setConfig(fieldConfigConverter.toJson(update.getConfig()));
        }

        existingEntity.setRequired(update.isRequired());
        existingEntity.setUniqueValue(update.isUniqueValue());
        existingEntity.setSortable(update.isSortable());
        existingEntity.setFilterable(update.isFilterable());

        existingEntity = fieldRepository.save(existingEntity);

        changeRecordService.create(
                "content_field", fieldId, "updated",
                "{}", "user:" + tenantInfo.tenantId(), "draft");

        eventPublisher.publishEvent(new ContentDefinitionChangedEvent(definition.getAppId(), tenantInfo.tenantId()));

        return fieldMapper.toDomain(existingEntity);
    }

    public void deleteField(UUID fieldId) {
        var tenantInfo = TenantContext.getTenantInformation();
        ContentFieldEntity entity = resolveField(fieldId);
        ContentDefinitionEntity definition = resolveDefinition(entity.getDefinitionId());

        String fieldType = entity.getFieldType();

        if ("REFERENCE".equals(fieldType)) {
            List<ContentFieldEntity> mirrorFields = fieldRepository.findMirrorFieldsBySourceFieldId("{\"sourceFieldId\":\"" + fieldId + "\"}");
            for (ContentFieldEntity mirror : mirrorFields) {
                mirror.getAuditInfo().setDeletedAt(OffsetDateTime.now());
                fieldRepository.save(mirror);
            }
        }

        if ("MIRROR".equals(fieldType)) {
            MirrorFieldConfig mirrorConfig = (MirrorFieldConfig) fieldConfigConverter.fromJson(
                    entity.getConfig(), FieldType.MIRROR);
            fieldRepository.findByIdAndAuditInfoDeletedAtIsNull(mirrorConfig.sourceFieldId())
                    .ifPresent(refEntity -> {
                        refEntity.getAuditInfo().setDeletedAt(OffsetDateTime.now());
                        fieldRepository.save(refEntity);
                    });
        }

        entity.getAuditInfo().setDeletedAt(OffsetDateTime.now());
        fieldRepository.save(entity);

        changeRecordService.create(
                "content_field", fieldId, "deleted",
                "{}", "user:" + tenantInfo.tenantId(), "draft");

        eventPublisher.publishEvent(new ContentDefinitionChangedEvent(definition.getAppId(), tenantInfo.tenantId()));
    }

    public void reorderFields(UUID definitionId, List<UUID> fieldIds) {
        var tenantInfo = TenantContext.getTenantInformation();
        ContentDefinitionEntity definition = resolveDefinition(definitionId);

        for (int i = 0; i < fieldIds.size(); i++) {
            ContentFieldEntity entity = fieldRepository.findByIdAndAuditInfoDeletedAtIsNull(fieldIds.get(i))
                    .orElseThrow(() -> new NotFoundException("content_field.not_found"));
            entity.setPosition(i);
            fieldRepository.save(entity);
        }

        changeRecordService.create(
                "content_field", definitionId, "reordered",
                "{}", "user:" + tenantInfo.tenantId(), "draft");

        eventPublisher.publishEvent(new ContentDefinitionChangedEvent(definition.getAppId(), tenantInfo.tenantId()));
    }

    @Transactional(readOnly = true)
    public List<FieldDefinition> listByDefinition(UUID definitionId) {
        List<ContentFieldEntity> entities =
                fieldRepository.findByDefinitionIdAndAuditInfoDeletedAtIsNullOrderByPositionAsc(definitionId);
        return fieldMapper.toDomainList(entities);
    }

    private void createMirrorField(
            ContentDefinitionEntity sourceDefinition,
            ContentFieldEntity sourceField,
            ReferenceFieldConfig refConfig,
            UUID tenantId) {

        UUID targetDefId = refConfig.targetDefinitionId();
        ContentDefinitionEntity targetDefinition = resolveDefinition(targetDefId);

        String mirrorKey = sourceDefinition.getName().toLowerCase() + "s";

        if (!fieldRepository.existsByFieldKeyAndDefinitionIdAndAuditInfoDeletedAtIsNull(mirrorKey, targetDefId)) {
            int mirrorPosition = fieldRepository.countByDefinitionIdAndAuditInfoDeletedAtIsNull(targetDefId);

            MirrorFieldConfig mirrorConfig = new MirrorFieldConfig(
                    sourceDefinition.getId(), sourceField.getId(), sourceDefinition.getAppId());

            ContentFieldEntity mirrorEntity = new ContentFieldEntity();
            mirrorEntity.setId(UuidGenerator.generate());
            mirrorEntity.setTenantId(tenantId);
            mirrorEntity.setDefinition(targetDefinition);
            mirrorEntity.setFieldKey(mirrorKey);
            mirrorEntity.setFieldType("MIRROR");
            mirrorEntity.setPosition(mirrorPosition);
            mirrorEntity.setConfig(fieldConfigConverter.toJson(mirrorConfig));

            fieldRepository.save(mirrorEntity);
        }
    }

    private ContentDefinitionEntity resolveDefinition(UUID definitionId) {
        return definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(definitionId)
                .orElseThrow(() -> new NotFoundException("content_definition.not_found"));
    }

    private ContentFieldEntity resolveField(UUID fieldId) {
        return fieldRepository.findByIdAndAuditInfoDeletedAtIsNull(fieldId)
                .orElseThrow(() -> new NotFoundException("content_field.not_found"));
    }

    private void checkFieldKeyUniqueness(String fieldKey, UUID definitionId) {
        if (fieldRepository.existsByFieldKeyAndDefinitionIdAndAuditInfoDeletedAtIsNull(fieldKey, definitionId)) {
            throw new ConflictException("content_field.field_key_already_exists");
        }
    }

    private void checkFieldKeyUniquenessForUpdate(String fieldKey, UUID definitionId, UUID excludeId) {
        if (fieldRepository.existsByFieldKeyAndDefinitionIdAndIdNotAndAuditInfoDeletedAtIsNull(fieldKey, definitionId, excludeId)) {
            throw new ConflictException("content_field.field_key_already_exists");
        }
    }
}

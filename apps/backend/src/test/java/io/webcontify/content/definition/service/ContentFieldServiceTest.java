package io.webcontify.content.definition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import io.webcontify.shared.exception.ValidationException;
import io.webcontify.shared.model.AuditInfo;
import io.webcontify.shared.model.TenantInformation;
import io.webcontify.shared.security.TenantAwareAuthentication;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ContentFieldServiceTest {

    private static final UUID TEST_TENANT_ID = UUID.randomUUID();
    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final UUID TEST_APP_ID = UUID.randomUUID();
    private static final UUID TEST_DEFINITION_ID = UUID.randomUUID();

    @Mock private ContentFieldRepository fieldRepository;
    @Mock private ContentDefinitionRepository definitionRepository;
    @Mock private ContentFieldMapper fieldMapper;
    @Mock private FieldConfigConverter fieldConfigConverter;
    @Mock private ContentFieldConfigValidator configValidator;
    @Mock private ContentReferenceValidator referenceValidator;
    @Mock private ChangeRecordService changeRecordService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ContentFieldService service;

    @BeforeEach
    void setUp() {
        TenantInformation info = new TenantInformation(TEST_TENANT_ID, false);
        var auth = new TenantAwareAuthentication(
                info, TEST_USER_ID, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        service = new ContentFieldService(
                fieldRepository, definitionRepository, fieldMapper, fieldConfigConverter,
                configValidator, referenceValidator, changeRecordService, eventPublisher);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // --- addField ---

    @Test
    void shouldAddFieldWithNextPositionAndValidateConfig() {
        FieldDefinition input = createFieldDefinition("title", FieldType.TEXT);
        ContentDefinitionEntity defEntity = createDefinitionEntity();

        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_DEFINITION_ID))
                .thenReturn(Optional.of(defEntity));
        when(fieldRepository.existsByFieldKeyAndDefinitionIdAndAuditInfoDeletedAtIsNull("title", TEST_DEFINITION_ID))
                .thenReturn(false);
        when(fieldRepository.countByDefinitionIdAndAuditInfoDeletedAtIsNull(TEST_DEFINITION_ID))
                .thenReturn(3);

        ContentFieldEntity savedEntity = createFieldEntity("title", "TEXT", 3);
        when(fieldMapper.toEntity(any(FieldDefinition.class))).thenReturn(savedEntity);
        when(fieldRepository.save(any(ContentFieldEntity.class))).thenReturn(savedEntity);

        FieldDefinition result = createFieldDefinition("title", FieldType.TEXT);
        result.setPosition(3);
        result.setId(savedEntity.getId());
        when(fieldMapper.toDomain(savedEntity)).thenReturn(result);

        FieldDefinition created = service.addField(TEST_DEFINITION_ID, input);

        assertThat(created.getFieldKey()).isEqualTo("title");
        assertThat(created.getPosition()).isEqualTo(3);
        verify(configValidator).validate(FieldType.TEXT, input.getConfig());
        verify(fieldRepository).save(any(ContentFieldEntity.class));
    }

    @Test
    void shouldThrowConflictOnDuplicateFieldKey() {
        FieldDefinition input = createFieldDefinition("title", FieldType.TEXT);
        ContentDefinitionEntity defEntity = createDefinitionEntity();

        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_DEFINITION_ID))
                .thenReturn(Optional.of(defEntity));
        when(fieldRepository.existsByFieldKeyAndDefinitionIdAndAuditInfoDeletedAtIsNull("title", TEST_DEFINITION_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> service.addField(TEST_DEFINITION_ID, input))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("content_field.field_key_already_exists");
    }

    @Test
    void shouldValidateReferenceAndAutoCreateMirrorOnTarget() {
        UUID targetDefId = UUID.randomUUID();
        ReferenceFieldConfig refConfig = new ReferenceFieldConfig(
                targetDefId, RelationType.MANY_TO_ONE, OnDeleteStrategy.CASCADE);
        FieldDefinition input = createFieldDefinition("authorRef", FieldType.REFERENCE);
        input.setConfig(refConfig);

        ContentDefinitionEntity sourceDefEntity = createDefinitionEntity();
        sourceDefEntity.setName("Article");

        ContentDefinitionEntity targetDefEntity = new ContentDefinitionEntity();
        targetDefEntity.setId(targetDefId);
        targetDefEntity.setAppId(TEST_APP_ID);
        targetDefEntity.setTenantId(TEST_TENANT_ID);
        targetDefEntity.setName("Author");

        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_DEFINITION_ID))
                .thenReturn(Optional.of(sourceDefEntity));
        when(fieldRepository.existsByFieldKeyAndDefinitionIdAndAuditInfoDeletedAtIsNull("authorRef", TEST_DEFINITION_ID))
                .thenReturn(false);
        when(fieldRepository.countByDefinitionIdAndAuditInfoDeletedAtIsNull(TEST_DEFINITION_ID))
                .thenReturn(0);

        ContentFieldEntity savedRefEntity = createFieldEntity("authorRef", "REFERENCE", 0);
        when(fieldMapper.toEntity(any(FieldDefinition.class))).thenReturn(savedRefEntity);
        when(fieldRepository.save(any(ContentFieldEntity.class))).thenReturn(savedRefEntity);

        FieldDefinition refResult = createFieldDefinition("authorRef", FieldType.REFERENCE);
        refResult.setId(savedRefEntity.getId());
        refResult.setConfig(refConfig);
        when(fieldMapper.toDomain(savedRefEntity)).thenReturn(refResult);

        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(targetDefId))
                .thenReturn(Optional.of(targetDefEntity));
        when(fieldRepository.existsByFieldKeyAndDefinitionIdAndAuditInfoDeletedAtIsNull("articles", targetDefId))
                .thenReturn(false);
        when(fieldRepository.countByDefinitionIdAndAuditInfoDeletedAtIsNull(targetDefId))
                .thenReturn(2);
        when(fieldConfigConverter.toJson(any(MirrorFieldConfig.class))).thenReturn("{}");

        FieldDefinition created = service.addField(TEST_DEFINITION_ID, input);

        verify(referenceValidator).validateReference(TEST_APP_ID, refConfig);
        assertThat(created.getFieldKey()).isEqualTo("authorRef");

        // Verify mirror field was created on target
        ArgumentCaptor<ContentFieldEntity> mirrorCaptor = ArgumentCaptor.forClass(ContentFieldEntity.class);
        verify(fieldRepository, times(2)).save(mirrorCaptor.capture());
        ContentFieldEntity mirrorEntity = mirrorCaptor.getAllValues().get(1);
        assertThat(mirrorEntity.getFieldKey()).isEqualTo("articles");
        assertThat(mirrorEntity.getFieldType()).isEqualTo("MIRROR");
        assertThat(mirrorEntity.getPosition()).isEqualTo(2);
    }

    @Test
    void shouldSetPositionToCountOfExistingFields() {
        FieldDefinition input = createFieldDefinition("description", FieldType.TEXT);
        ContentDefinitionEntity defEntity = createDefinitionEntity();

        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_DEFINITION_ID))
                .thenReturn(Optional.of(defEntity));
        when(fieldRepository.existsByFieldKeyAndDefinitionIdAndAuditInfoDeletedAtIsNull("description", TEST_DEFINITION_ID))
                .thenReturn(false);
        when(fieldRepository.countByDefinitionIdAndAuditInfoDeletedAtIsNull(TEST_DEFINITION_ID))
                .thenReturn(5);

        ContentFieldEntity savedEntity = createFieldEntity("description", "TEXT", 5);
        when(fieldMapper.toEntity(any(FieldDefinition.class))).thenReturn(savedEntity);
        when(fieldRepository.save(any(ContentFieldEntity.class))).thenReturn(savedEntity);

        FieldDefinition result = createFieldDefinition("description", FieldType.TEXT);
        result.setPosition(5);
        result.setId(savedEntity.getId());
        when(fieldMapper.toDomain(savedEntity)).thenReturn(result);

        FieldDefinition created = service.addField(TEST_DEFINITION_ID, input);

        assertThat(created.getPosition()).isEqualTo(5);
    }

    // --- updateField ---

    @Test
    void shouldUpdateFieldKeyAndConfigAndOptions() {
        UUID fieldId = UUID.randomUUID();
        ContentFieldEntity existingEntity = createFieldEntity("oldKey", "TEXT", 0);
        existingEntity.setId(fieldId);
        existingEntity.setDefinitionId(TEST_DEFINITION_ID);

        ContentDefinitionEntity defEntity = createDefinitionEntity();

        when(fieldRepository.findByIdAndAuditInfoDeletedAtIsNull(fieldId))
                .thenReturn(Optional.of(existingEntity));
        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_DEFINITION_ID))
                .thenReturn(Optional.of(defEntity));
        when(fieldRepository.existsByFieldKeyAndDefinitionIdAndIdNotAndAuditInfoDeletedAtIsNull("newKey", TEST_DEFINITION_ID, fieldId))
                .thenReturn(false);
        when(fieldRepository.save(existingEntity)).thenReturn(existingEntity);

        FieldDefinition updated = createFieldDefinition("newKey", FieldType.TEXT);
        updated.setId(fieldId);
        updated.setRequired(true);
        updated.setSortable(true);
        when(fieldMapper.toDomain(existingEntity)).thenReturn(updated);

        FieldDefinition update = new FieldDefinition();
        update.setFieldKey("newKey");
        update.setFieldType(FieldType.TEXT);
        update.setRequired(true);
        update.setSortable(true);

        FieldDefinition result = service.updateField(fieldId, update);

        assertThat(result.getFieldKey()).isEqualTo("newKey");
        assertThat(result.isRequired()).isTrue();
        assertThat(result.isSortable()).isTrue();
        verify(fieldRepository).save(existingEntity);
    }

    @Test
    void shouldThrowValidationExceptionOnFieldTypeChange() {
        UUID fieldId = UUID.randomUUID();
        ContentFieldEntity existingEntity = createFieldEntity("title", "TEXT", 0);
        existingEntity.setId(fieldId);
        existingEntity.setDefinitionId(TEST_DEFINITION_ID);

        when(fieldRepository.findByIdAndAuditInfoDeletedAtIsNull(fieldId))
                .thenReturn(Optional.of(existingEntity));

        FieldDefinition update = new FieldDefinition();
        update.setFieldType(FieldType.INTEGER);

        assertThatThrownBy(() -> service.updateField(fieldId, update))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void shouldThrowConflictOnDuplicateFieldKeyDuringUpdate() {
        UUID fieldId = UUID.randomUUID();
        ContentFieldEntity existingEntity = createFieldEntity("oldKey", "TEXT", 0);
        existingEntity.setId(fieldId);
        existingEntity.setDefinitionId(TEST_DEFINITION_ID);

        ContentDefinitionEntity defEntity = createDefinitionEntity();

        when(fieldRepository.findByIdAndAuditInfoDeletedAtIsNull(fieldId))
                .thenReturn(Optional.of(existingEntity));
        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_DEFINITION_ID))
                .thenReturn(Optional.of(defEntity));
        when(fieldRepository.existsByFieldKeyAndDefinitionIdAndIdNotAndAuditInfoDeletedAtIsNull("takenKey", TEST_DEFINITION_ID, fieldId))
                .thenReturn(true);

        FieldDefinition update = new FieldDefinition();
        update.setFieldKey("takenKey");
        update.setFieldType(FieldType.TEXT);

        assertThatThrownBy(() -> service.updateField(fieldId, update))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("content_field.field_key_already_exists");
    }

    @Test
    void shouldPublishChangedEventOnUpdate() {
        UUID fieldId = UUID.randomUUID();
        ContentFieldEntity existingEntity = createFieldEntity("title", "TEXT", 0);
        existingEntity.setId(fieldId);
        existingEntity.setDefinitionId(TEST_DEFINITION_ID);

        ContentDefinitionEntity defEntity = createDefinitionEntity();

        when(fieldRepository.findByIdAndAuditInfoDeletedAtIsNull(fieldId))
                .thenReturn(Optional.of(existingEntity));
        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_DEFINITION_ID))
                .thenReturn(Optional.of(defEntity));
        when(fieldRepository.save(existingEntity)).thenReturn(existingEntity);

        FieldDefinition updated = createFieldDefinition("title", FieldType.TEXT);
        updated.setId(fieldId);
        when(fieldMapper.toDomain(existingEntity)).thenReturn(updated);

        FieldDefinition update = new FieldDefinition();
        update.setFieldType(FieldType.TEXT);
        update.setRequired(true);

        service.updateField(fieldId, update);

        ArgumentCaptor<ContentDefinitionChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(ContentDefinitionChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().appId()).isEqualTo(TEST_APP_ID);
        assertThat(eventCaptor.getValue().tenantId()).isEqualTo(TEST_TENANT_ID);
    }

    // --- deleteField ---

    @Test
    void shouldSoftDeleteField() {
        UUID fieldId = UUID.randomUUID();
        ContentFieldEntity entity = createFieldEntity("title", "TEXT", 0);
        entity.setId(fieldId);
        entity.setDefinitionId(TEST_DEFINITION_ID);
        AuditInfo auditInfo = new AuditInfo();
        entity.setAuditInfo(auditInfo);

        ContentDefinitionEntity defEntity = createDefinitionEntity();

        when(fieldRepository.findByIdAndAuditInfoDeletedAtIsNull(fieldId))
                .thenReturn(Optional.of(entity));
        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_DEFINITION_ID))
                .thenReturn(Optional.of(defEntity));

        service.deleteField(fieldId);

        assertThat(entity.getAuditInfo().getDeletedAt()).isNotNull();
        verify(fieldRepository).save(entity);
    }

    @Test
    void shouldDeletePairedMirrorWhenDeletingReference() {
        UUID fieldId = UUID.randomUUID();
        UUID mirrorFieldId = UUID.randomUUID();

        ContentFieldEntity refEntity = createFieldEntity("authorRef", "REFERENCE", 0);
        refEntity.setId(fieldId);
        refEntity.setDefinitionId(TEST_DEFINITION_ID);
        AuditInfo refAudit = new AuditInfo();
        refEntity.setAuditInfo(refAudit);

        ContentFieldEntity mirrorEntity = createFieldEntity("articles", "MIRROR", 2);
        mirrorEntity.setId(mirrorFieldId);
        mirrorEntity.setDefinitionId(UUID.randomUUID());
        AuditInfo mirrorAudit = new AuditInfo();
        mirrorEntity.setAuditInfo(mirrorAudit);

        ContentDefinitionEntity defEntity = createDefinitionEntity();

        when(fieldRepository.findByIdAndAuditInfoDeletedAtIsNull(fieldId))
                .thenReturn(Optional.of(refEntity));
        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_DEFINITION_ID))
                .thenReturn(Optional.of(defEntity));
        when(fieldRepository.findMirrorFieldsBySourceFieldId(fieldId.toString()))
                .thenReturn(List.of(mirrorEntity));

        service.deleteField(fieldId);

        assertThat(refEntity.getAuditInfo().getDeletedAt()).isNotNull();
        assertThat(mirrorEntity.getAuditInfo().getDeletedAt()).isNotNull();
        verify(fieldRepository).save(refEntity);
        verify(fieldRepository).save(mirrorEntity);
    }

    @Test
    void shouldDeletePairedReferenceWhenDeletingMirror() {
        UUID mirrorFieldId = UUID.randomUUID();
        UUID sourceFieldId = UUID.randomUUID();
        UUID sourceDefId = UUID.randomUUID();

        MirrorFieldConfig mirrorConfig = new MirrorFieldConfig(sourceDefId, sourceFieldId, TEST_APP_ID);

        ContentFieldEntity mirrorEntity = createFieldEntity("articles", "MIRROR", 2);
        mirrorEntity.setId(mirrorFieldId);
        mirrorEntity.setDefinitionId(TEST_DEFINITION_ID);
        AuditInfo mirrorAudit = new AuditInfo();
        mirrorEntity.setAuditInfo(mirrorAudit);

        ContentFieldEntity refEntity = createFieldEntity("authorRef", "REFERENCE", 0);
        refEntity.setId(sourceFieldId);
        refEntity.setDefinitionId(sourceDefId);
        AuditInfo refAudit = new AuditInfo();
        refEntity.setAuditInfo(refAudit);

        ContentDefinitionEntity defEntity = createDefinitionEntity();

        when(fieldRepository.findByIdAndAuditInfoDeletedAtIsNull(mirrorFieldId))
                .thenReturn(Optional.of(mirrorEntity));
        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_DEFINITION_ID))
                .thenReturn(Optional.of(defEntity));
        when(fieldConfigConverter.fromJson(mirrorEntity.getConfig(), FieldType.MIRROR))
                .thenReturn(mirrorConfig);
        when(fieldRepository.findByIdAndAuditInfoDeletedAtIsNull(sourceFieldId))
                .thenReturn(Optional.of(refEntity));

        service.deleteField(mirrorFieldId);

        assertThat(mirrorEntity.getAuditInfo().getDeletedAt()).isNotNull();
        assertThat(refEntity.getAuditInfo().getDeletedAt()).isNotNull();
        verify(fieldRepository).save(mirrorEntity);
        verify(fieldRepository).save(refEntity);
    }

    // --- reorderFields ---

    @Test
    void shouldUpdatePositionsInGivenOrder() {
        UUID fieldId1 = UUID.randomUUID();
        UUID fieldId2 = UUID.randomUUID();
        UUID fieldId3 = UUID.randomUUID();

        ContentFieldEntity entity1 = createFieldEntity("a", "TEXT", 2);
        entity1.setId(fieldId1);
        ContentFieldEntity entity2 = createFieldEntity("b", "TEXT", 0);
        entity2.setId(fieldId2);
        ContentFieldEntity entity3 = createFieldEntity("c", "TEXT", 1);
        entity3.setId(fieldId3);

        ContentDefinitionEntity defEntity = createDefinitionEntity();

        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_DEFINITION_ID))
                .thenReturn(Optional.of(defEntity));
        when(fieldRepository.findByIdAndAuditInfoDeletedAtIsNull(fieldId1)).thenReturn(Optional.of(entity1));
        when(fieldRepository.findByIdAndAuditInfoDeletedAtIsNull(fieldId2)).thenReturn(Optional.of(entity2));
        when(fieldRepository.findByIdAndAuditInfoDeletedAtIsNull(fieldId3)).thenReturn(Optional.of(entity3));

        service.reorderFields(TEST_DEFINITION_ID, List.of(fieldId1, fieldId2, fieldId3));

        assertThat(entity1.getPosition()).isEqualTo(0);
        assertThat(entity2.getPosition()).isEqualTo(1);
        assertThat(entity3.getPosition()).isEqualTo(2);
        verify(fieldRepository, times(3)).save(any(ContentFieldEntity.class));
    }

    @Test
    void shouldPublishEventOnReorder() {
        UUID fieldId1 = UUID.randomUUID();

        ContentFieldEntity entity1 = createFieldEntity("a", "TEXT", 0);
        entity1.setId(fieldId1);

        ContentDefinitionEntity defEntity = createDefinitionEntity();

        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_DEFINITION_ID))
                .thenReturn(Optional.of(defEntity));
        when(fieldRepository.findByIdAndAuditInfoDeletedAtIsNull(fieldId1)).thenReturn(Optional.of(entity1));

        service.reorderFields(TEST_DEFINITION_ID, List.of(fieldId1));

        ArgumentCaptor<ContentDefinitionChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(ContentDefinitionChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().appId()).isEqualTo(TEST_APP_ID);
        assertThat(eventCaptor.getValue().tenantId()).isEqualTo(TEST_TENANT_ID);
    }

    // --- helper methods ---

    private FieldDefinition createFieldDefinition(String fieldKey, FieldType fieldType) {
        FieldDefinition field = new FieldDefinition();
        field.setFieldKey(fieldKey);
        field.setFieldType(fieldType);
        return field;
    }

    private ContentFieldEntity createFieldEntity(String fieldKey, String fieldType, int position) {
        ContentFieldEntity entity = new ContentFieldEntity();
        entity.setId(UUID.randomUUID());
        entity.setDefinitionId(TEST_DEFINITION_ID);
        entity.setFieldKey(fieldKey);
        entity.setFieldType(fieldType);
        entity.setPosition(position);
        entity.setTenantId(TEST_TENANT_ID);
        return entity;
    }

    private ContentDefinitionEntity createDefinitionEntity() {
        ContentDefinitionEntity entity = new ContentDefinitionEntity();
        entity.setId(TEST_DEFINITION_ID);
        entity.setAppId(TEST_APP_ID);
        entity.setTenantId(TEST_TENANT_ID);
        entity.setName("Article");
        entity.setSlug("article");
        return entity;
    }
}

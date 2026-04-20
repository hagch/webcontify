package io.webcontify.content.definition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.webcontify.app.repository.AppRepository;
import io.webcontify.app.repository.entity.AppEntity;
import io.webcontify.shared.event.ContentDefinitionChangedEvent;
import io.webcontify.shared.event.ContentDefinitionDeletedEvent;
import io.webcontify.content.definition.mapper.ContentDefinitionMapper;
import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.ContentDefinitionListResult;
import io.webcontify.content.definition.repository.ContentDefinitionRepository;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.identity.model.CheckPermissionRequest;
import io.webcontify.identity.model.ListPermittedObjectsRequest;
import io.webcontify.identity.model.RelationshipTuple;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.shared.changelog.service.ChangeRecordService;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.model.AuditInfo;
import io.webcontify.shared.model.TenantInformation;
import io.webcontify.shared.security.TenantAwareAuthentication;
import io.webcontify.shared.util.SlugGenerator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
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
class ContentDefinitionServiceTest {

    private static final UUID TEST_TENANT_ID = UUID.randomUUID();
    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final UUID TEST_APP_ID = UUID.randomUUID();

    @Mock
    private ContentDefinitionRepository definitionRepository;

    @Mock
    private io.webcontify.content.definition.repository.ContentFieldRepository fieldRepository;

    @Mock
    private AppRepository appRepository;

    @Mock
    private ContentDefinitionMapper definitionMapper;

    @Mock
    private SlugGenerator slugGenerator;

    @Mock
    private ChangeRecordService changeRecordService;

    @Mock
    private PermissionService permissionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ContentDefinitionService service;

    @BeforeEach
    void setUp() {
        TenantInformation info = new TenantInformation(TEST_TENANT_ID, false);
        var auth = new TenantAwareAuthentication(info, TEST_USER_ID, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        service = new ContentDefinitionService(
                definitionRepository, fieldRepository, appRepository, definitionMapper,
                slugGenerator, changeRecordService, permissionService, eventPublisher);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // --- create ---

    @Test
    void shouldCreateDefinitionWithGeneratedSlugAndIds() {
        ContentDefinition input = new ContentDefinition();
        input.setName("Article");

        AppEntity appEntity = createAppEntity();
        when(appRepository.findBySlugAndAuditInfoDeletedAtIsNull("my-app")).thenReturn(Optional.of(appEntity));
        when(permissionService.check(any(CheckPermissionRequest.class))).thenReturn(true);
        when(definitionRepository.existsByNameAndAppIdAndAuditInfoDeletedAtIsNull("Article", TEST_APP_ID))
                .thenReturn(false);
        when(slugGenerator.generateUnique(eq("Article"), any(Predicate.class))).thenReturn("article");

        ContentDefinitionEntity savedEntity = createDefinitionEntity("Article", "article");
        when(definitionMapper.toEntity(any(ContentDefinition.class))).thenReturn(savedEntity);
        when(definitionRepository.save(any(ContentDefinitionEntity.class))).thenReturn(savedEntity);

        ContentDefinition result = new ContentDefinition();
        result.setId(savedEntity.getId());
        result.setName("Article");
        result.setSlug("article");
        result.setTenantId(TEST_TENANT_ID);
        result.setAppId(TEST_APP_ID);
        when(definitionMapper.toDomain(savedEntity)).thenReturn(result);

        ContentDefinition created = service.create("my-app", input);

        assertThat(created.getName()).isEqualTo("Article");
        assertThat(created.getSlug()).isEqualTo("article");
        assertThat(created.getTenantId()).isEqualTo(TEST_TENANT_ID);
        assertThat(created.getAppId()).isEqualTo(TEST_APP_ID);
        assertThat(created.getId()).isNotNull();
        verify(definitionRepository).save(any(ContentDefinitionEntity.class));
    }

    @Test
    void shouldThrowConflictOnDuplicateName() {
        ContentDefinition input = new ContentDefinition();
        input.setName("Existing");

        AppEntity appEntity = createAppEntity();
        when(appRepository.findBySlugAndAuditInfoDeletedAtIsNull("my-app")).thenReturn(Optional.of(appEntity));
        when(permissionService.check(any(CheckPermissionRequest.class))).thenReturn(true);
        when(definitionRepository.existsByNameAndAppIdAndAuditInfoDeletedAtIsNull("Existing", TEST_APP_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create("my-app", input))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("content_definition.name_already_exists");
    }

    @Test
    void shouldGrantPermissionOnCreate() {
        ContentDefinition input = new ContentDefinition();
        input.setName("Article");

        AppEntity appEntity = createAppEntity();
        when(appRepository.findBySlugAndAuditInfoDeletedAtIsNull("my-app")).thenReturn(Optional.of(appEntity));
        when(permissionService.check(any(CheckPermissionRequest.class))).thenReturn(true);
        when(definitionRepository.existsByNameAndAppIdAndAuditInfoDeletedAtIsNull("Article", TEST_APP_ID))
                .thenReturn(false);
        when(slugGenerator.generateUnique(eq("Article"), any(Predicate.class))).thenReturn("article");

        ContentDefinitionEntity savedEntity = createDefinitionEntity("Article", "article");
        when(definitionMapper.toEntity(any(ContentDefinition.class))).thenReturn(savedEntity);
        when(definitionRepository.save(any(ContentDefinitionEntity.class))).thenReturn(savedEntity);

        ContentDefinition result = new ContentDefinition();
        result.setId(savedEntity.getId());
        result.setAppId(TEST_APP_ID);
        when(definitionMapper.toDomain(savedEntity)).thenReturn(result);

        service.create("my-app", input);

        ArgumentCaptor<RelationshipTuple> tupleCaptor = ArgumentCaptor.forClass(RelationshipTuple.class);
        verify(permissionService).grantRelation(tupleCaptor.capture());
        RelationshipTuple tuple = tupleCaptor.getValue();
        assertThat(tuple.user()).isEqualTo("app:" + TEST_APP_ID);
        assertThat(tuple.relation()).isEqualTo("parent");
        assertThat(tuple.object()).startsWith("content_definition:");
    }

    @Test
    void shouldPublishChangedEventOnCreate() {
        ContentDefinition input = new ContentDefinition();
        input.setName("Article");

        AppEntity appEntity = createAppEntity();
        when(appRepository.findBySlugAndAuditInfoDeletedAtIsNull("my-app")).thenReturn(Optional.of(appEntity));
        when(permissionService.check(any(CheckPermissionRequest.class))).thenReturn(true);
        when(definitionRepository.existsByNameAndAppIdAndAuditInfoDeletedAtIsNull("Article", TEST_APP_ID))
                .thenReturn(false);
        when(slugGenerator.generateUnique(eq("Article"), any(Predicate.class))).thenReturn("article");

        ContentDefinitionEntity savedEntity = createDefinitionEntity("Article", "article");
        when(definitionMapper.toEntity(any(ContentDefinition.class))).thenReturn(savedEntity);
        when(definitionRepository.save(any(ContentDefinitionEntity.class))).thenReturn(savedEntity);

        ContentDefinition result = new ContentDefinition();
        result.setId(savedEntity.getId());
        result.setAppId(TEST_APP_ID);
        when(definitionMapper.toDomain(savedEntity)).thenReturn(result);

        service.create("my-app", input);

        ArgumentCaptor<ContentDefinitionChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(ContentDefinitionChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ContentDefinitionChangedEvent event = eventCaptor.getValue();
        assertThat(event.appId()).isEqualTo(TEST_APP_ID);
        assertThat(event.tenantId()).isEqualTo(TEST_TENANT_ID);
    }

    // --- getById ---

    @Test
    void shouldReturnDefinitionWithFieldsById() {
        UUID defId = UUID.randomUUID();
        ContentDefinitionEntity entity = createDefinitionEntity("Article", "article");
        entity.setId(defId);

        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(defId)).thenReturn(Optional.of(entity));

        ContentDefinition expected = new ContentDefinition();
        expected.setId(defId);
        expected.setName("Article");
        expected.setFields(List.of());
        when(definitionMapper.toDomain(entity)).thenReturn(expected);

        ContentDefinition result = service.getById(defId);

        assertThat(result.getId()).isEqualTo(defId);
        assertThat(result.getName()).isEqualTo("Article");
        assertThat(result.getFields()).isNotNull();
    }

    @Test
    void shouldThrowNotFoundForMissingDefinition() {
        UUID defId = UUID.randomUUID();
        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(defId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(defId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("content_definition.not_found");
    }

    // --- listByApp ---

    @Test
    void shouldReturnAllDefinitionsForApp() {
        AppEntity appEntity = createAppEntity();
        when(appRepository.findBySlugAndAuditInfoDeletedAtIsNull("my-app")).thenReturn(Optional.of(appEntity));
        when(permissionService.check(any(CheckPermissionRequest.class))).thenReturn(true);

        ContentDefinitionEntity entity1 = createDefinitionEntity("Article", "article");
        ContentDefinitionEntity entity2 = createDefinitionEntity("Author", "author");
        when(definitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(TEST_APP_ID))
                .thenReturn(List.of(entity1, entity2));

        ContentDefinition def1 = new ContentDefinition();
        def1.setName("Article");
        ContentDefinition def2 = new ContentDefinition();
        def2.setName("Author");
        when(definitionMapper.toDomainList(List.of(entity1, entity2))).thenReturn(List.of(def1, def2));

        var listResult = service.listByApp("my-app");

        assertThat(listResult.items()).hasSize(2);
        assertThat(listResult.items().get(0).getName()).isEqualTo("Article");
        assertThat(listResult.items().get(1).getName()).isEqualTo("Author");
        assertThat(listResult.referencedDefinitions()).isEmpty();
    }

    @Test
    void shouldReturnOnlyAccessibleDefinitionsWhenUserLacksAppViewerButHasSpecific() {
        AppEntity appEntity = createAppEntity();
        UUID def1 = UUID.randomUUID();
        when(appRepository.findBySlugAndAuditInfoDeletedAtIsNull("my-app")).thenReturn(Optional.of(appEntity));
        when(permissionService.check(any(CheckPermissionRequest.class))).thenReturn(false);
        when(permissionService.listObjects(argThat(r ->
                r != null && "viewer".equals(r.action())
                        && "content_definition".equals(r.resourceType()))))
                .thenReturn(List.of(def1));

        ContentDefinitionEntity entity1 = createDefinitionEntity("Article", "article");
        entity1.setId(def1);
        when(definitionRepository.findByIdInAndAppIdAndAuditInfoDeletedAtIsNull(
                List.of(def1), TEST_APP_ID))
                .thenReturn(List.of(entity1));

        ContentDefinition d1 = new ContentDefinition();
        d1.setName("Article");
        when(definitionMapper.toDomainList(List.of(entity1))).thenReturn(List.of(d1));

        var listResult = service.listByApp("my-app");

        assertThat(listResult.items()).hasSize(1);
        assertThat(listResult.items().get(0).getName()).isEqualTo("Article");
        verify(definitionRepository, never()).findByAppIdAndAuditInfoDeletedAtIsNull(any());
    }

    @Test
    void shouldReturnEmptyWhenUserHasNoAppAccessAndNoSpecificAccess() {
        AppEntity appEntity = createAppEntity();
        when(appRepository.findBySlugAndAuditInfoDeletedAtIsNull("my-app")).thenReturn(Optional.of(appEntity));
        when(permissionService.check(any(CheckPermissionRequest.class))).thenReturn(false);
        when(permissionService.listObjects(any(ListPermittedObjectsRequest.class)))
                .thenReturn(List.of());

        var listResult = service.listByApp("my-app");

        assertThat(listResult.items()).isEmpty();
        assertThat(listResult.referencedDefinitions()).isEmpty();
        verify(definitionRepository, never()).findByAppIdAndAuditInfoDeletedAtIsNull(any());
        verify(definitionRepository, never()).findByIdInAndAppIdAndAuditInfoDeletedAtIsNull(any(), any());
    }

    // --- update ---

    @Test
    void shouldUpdateNameAndDescription() {
        UUID defId = UUID.randomUUID();
        ContentDefinitionEntity existingEntity = createDefinitionEntity("Old Name", "old-name");
        existingEntity.setId(defId);
        existingEntity.setAppId(TEST_APP_ID);

        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(defId)).thenReturn(Optional.of(existingEntity));
        when(definitionRepository.existsByNameAndAppIdAndIdNotAndAuditInfoDeletedAtIsNull("New Name", TEST_APP_ID, defId))
                .thenReturn(false);

        ContentDefinitionEntity updatedEntity = createDefinitionEntity("New Name", "old-name");
        updatedEntity.setId(defId);
        updatedEntity.setDescription("New Description");
        when(definitionRepository.save(existingEntity)).thenReturn(updatedEntity);

        ContentDefinition updatedDomain = new ContentDefinition();
        updatedDomain.setId(defId);
        updatedDomain.setName("New Name");
        updatedDomain.setDescription("New Description");
        when(definitionMapper.toDomain(updatedEntity)).thenReturn(updatedDomain);

        ContentDefinition update = new ContentDefinition();
        update.setName("New Name");
        update.setDescription("New Description");

        ContentDefinition result = service.update(defId, update);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getDescription()).isEqualTo("New Description");
        verify(definitionMapper).updateEntity(existingEntity, update);
        verify(definitionRepository).save(existingEntity);
    }

    @Test
    void shouldThrowConflictOnDuplicateNameDuringUpdate() {
        UUID defId = UUID.randomUUID();
        ContentDefinitionEntity existingEntity = createDefinitionEntity("Old Name", "old-name");
        existingEntity.setId(defId);
        existingEntity.setAppId(TEST_APP_ID);

        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(defId)).thenReturn(Optional.of(existingEntity));
        when(definitionRepository.existsByNameAndAppIdAndIdNotAndAuditInfoDeletedAtIsNull("Taken Name", TEST_APP_ID, defId))
                .thenReturn(true);

        ContentDefinition update = new ContentDefinition();
        update.setName("Taken Name");

        assertThatThrownBy(() -> service.update(defId, update))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("content_definition.name_already_exists");
    }

    @Test
    void shouldKeepSlugUnchangedOnUpdate() {
        UUID defId = UUID.randomUUID();
        ContentDefinitionEntity existingEntity = createDefinitionEntity("Old Name", "old-name");
        existingEntity.setId(defId);
        existingEntity.setAppId(TEST_APP_ID);

        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(defId)).thenReturn(Optional.of(existingEntity));
        when(definitionRepository.existsByNameAndAppIdAndIdNotAndAuditInfoDeletedAtIsNull("New Name", TEST_APP_ID, defId))
                .thenReturn(false);
        when(definitionRepository.save(existingEntity)).thenReturn(existingEntity);

        ContentDefinition updatedDomain = new ContentDefinition();
        updatedDomain.setId(defId);
        updatedDomain.setSlug("old-name");
        when(definitionMapper.toDomain(existingEntity)).thenReturn(updatedDomain);

        ContentDefinition update = new ContentDefinition();
        update.setName("New Name");

        ContentDefinition result = service.update(defId, update);

        assertThat(result.getSlug()).isEqualTo("old-name");
    }

    @Test
    void shouldCreateRevisionOnUpdate() {
        UUID defId = UUID.randomUUID();
        ContentDefinitionEntity entity = new ContentDefinitionEntity();
        entity.setId(defId);
        entity.setName("Old Name");
        entity.setDescription("Old desc");
        entity.setSlug("old-name");
        entity.setAppId(TEST_APP_ID);

        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(defId)).thenReturn(Optional.of(entity));
        when(definitionRepository.save(any(ContentDefinitionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        doAnswer(invocation -> {
            ContentDefinitionEntity e = invocation.getArgument(0);
            ContentDefinition u = invocation.getArgument(1);
            e.setName(u.getName());
            e.setDescription(u.getDescription());
            return null;
        }).when(definitionMapper).updateEntity(any(ContentDefinitionEntity.class), any(ContentDefinition.class));

        ContentDefinition result = new ContentDefinition();
        result.setId(defId);
        result.setName("New Name");
        when(definitionMapper.toDomain(any(ContentDefinitionEntity.class))).thenReturn(result);
        when(definitionRepository.existsByNameAndAppIdAndIdNotAndAuditInfoDeletedAtIsNull("New Name", TEST_APP_ID, defId))
                .thenReturn(false);

        ContentDefinition update = new ContentDefinition();
        update.setName("New Name");
        update.setDescription("New desc");

        service.update(defId, update);

        verify(changeRecordService).create(
                eq("content_definition"), eq(defId), eq("updated"),
                argThat(patch -> patch.contains("replace")),
                argThat(source -> source.startsWith("user:")),
                eq("draft"));
    }

    @Test
    void shouldPublishChangedEventOnUpdate() {
        UUID defId = UUID.randomUUID();
        ContentDefinitionEntity entity = createDefinitionEntity("Old Name", "old-name");
        entity.setId(defId);
        entity.setAppId(TEST_APP_ID);

        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(defId)).thenReturn(Optional.of(entity));
        when(definitionRepository.save(entity)).thenReturn(entity);

        ContentDefinition updatedDomain = new ContentDefinition();
        updatedDomain.setId(defId);
        when(definitionMapper.toDomain(entity)).thenReturn(updatedDomain);

        ContentDefinition update = new ContentDefinition();
        update.setDescription("Updated desc");

        service.update(defId, update);

        ArgumentCaptor<ContentDefinitionChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(ContentDefinitionChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().appId()).isEqualTo(TEST_APP_ID);
        assertThat(eventCaptor.getValue().tenantId()).isEqualTo(TEST_TENANT_ID);
    }

    // --- delete ---

    @Test
    void shouldSoftDeleteDefinition() {
        UUID defId = UUID.randomUUID();
        ContentDefinitionEntity entity = createDefinitionEntity("Article", "article");
        entity.setId(defId);
        entity.setAppId(TEST_APP_ID);
        AuditInfo auditInfo = new AuditInfo();
        entity.setAuditInfo(auditInfo);

        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(defId)).thenReturn(Optional.of(entity));

        service.delete(defId);

        assertThat(entity.getAuditInfo().getDeletedAt()).isNotNull();
        verify(definitionRepository).save(entity);
    }

    @Test
    void shouldSoftDeleteMirrorsOfOwnReferenceFieldsWhenDefinitionDeleted() {
        UUID defId = UUID.randomUUID();
        ContentDefinitionEntity entity = createDefinitionEntity("Article", "article");
        entity.setId(defId);
        entity.setAppId(TEST_APP_ID);
        entity.setAuditInfo(new AuditInfo());
        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(defId))
                .thenReturn(Optional.of(entity));

        // Own REFERENCE field on this definition → another definition.
        UUID ownRefId = UUID.randomUUID();
        var ownRef = new io.webcontify.content.definition.repository.entity.ContentFieldEntity();
        ownRef.setId(ownRefId);
        ownRef.setDefinitionId(defId);
        ownRef.setFieldType("REFERENCE");
        ownRef.setAuditInfo(new AuditInfo());

        when(fieldRepository.findByDefinitionIdAndAuditInfoDeletedAtIsNullOrderByPositionAsc(defId))
                .thenReturn(List.of(ownRef));

        // A MIRROR on some other definition mirrors this REFERENCE.
        var mirrorOnOther = new io.webcontify.content.definition.repository.entity.ContentFieldEntity();
        mirrorOnOther.setId(UUID.randomUUID());
        mirrorOnOther.setAuditInfo(new AuditInfo());
        when(fieldRepository.findMirrorFieldsBySourceFieldId(
                "{\"sourceFieldId\":\"" + ownRefId + "\"}"))
                .thenReturn(List.of(mirrorOnOther));

        service.delete(defId);

        assertThat(ownRef.getAuditInfo().getDeletedAt()).isNotNull();
        assertThat(mirrorOnOther.getAuditInfo().getDeletedAt()).isNotNull();
        verify(fieldRepository).save(ownRef);
        verify(fieldRepository).save(mirrorOnOther);
    }

    @Test
    void shouldSoftDeleteIncomingReferenceFieldsWhenDefinitionDeleted() {
        UUID defId = UUID.randomUUID();
        ContentDefinitionEntity entity = createDefinitionEntity("Author", "author");
        entity.setId(defId);
        entity.setAppId(TEST_APP_ID);
        entity.setAuditInfo(new AuditInfo());
        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(defId))
                .thenReturn(Optional.of(entity));

        UUID refFieldId = UUID.randomUUID();
        var refField = new io.webcontify.content.definition.repository.entity.ContentFieldEntity();
        refField.setId(refFieldId);
        refField.setAuditInfo(new AuditInfo());
        when(fieldRepository.findReferenceFieldsByTargetDefinitionId(
                "{\"targetDefinitionId\":\"" + defId + "\"}"))
                .thenReturn(List.of(refField));

        var mirrorField = new io.webcontify.content.definition.repository.entity.ContentFieldEntity();
        mirrorField.setId(UUID.randomUUID());
        mirrorField.setAuditInfo(new AuditInfo());
        when(fieldRepository.findMirrorFieldsBySourceFieldId(
                "{\"sourceFieldId\":\"" + refFieldId + "\"}"))
                .thenReturn(List.of(mirrorField));

        service.delete(defId);

        assertThat(refField.getAuditInfo().getDeletedAt()).isNotNull();
        assertThat(mirrorField.getAuditInfo().getDeletedAt()).isNotNull();
        verify(fieldRepository).save(refField);
        verify(fieldRepository).save(mirrorField);
    }

    @Test
    void shouldRevokePermissionOnDelete() {
        UUID defId = UUID.randomUUID();
        ContentDefinitionEntity entity = createDefinitionEntity("Article", "article");
        entity.setId(defId);
        entity.setAppId(TEST_APP_ID);
        AuditInfo auditInfo = new AuditInfo();
        entity.setAuditInfo(auditInfo);

        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(defId)).thenReturn(Optional.of(entity));

        service.delete(defId);

        ArgumentCaptor<RelationshipTuple> tupleCaptor = ArgumentCaptor.forClass(RelationshipTuple.class);
        verify(permissionService).revokeRelation(tupleCaptor.capture());
        RelationshipTuple tuple = tupleCaptor.getValue();
        assertThat(tuple.user()).isEqualTo("app:" + TEST_APP_ID);
        assertThat(tuple.relation()).isEqualTo("parent");
        assertThat(tuple.object()).isEqualTo("content_definition:" + defId);
    }

    @Test
    void shouldPublishDeletedEventOnDelete() {
        UUID defId = UUID.randomUUID();
        ContentDefinitionEntity entity = createDefinitionEntity("Article", "article");
        entity.setId(defId);
        entity.setAppId(TEST_APP_ID);
        AuditInfo auditInfo = new AuditInfo();
        entity.setAuditInfo(auditInfo);

        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(defId)).thenReturn(Optional.of(entity));

        service.delete(defId);

        ArgumentCaptor<ContentDefinitionDeletedEvent> eventCaptor =
                ArgumentCaptor.forClass(ContentDefinitionDeletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ContentDefinitionDeletedEvent event = eventCaptor.getValue();
        assertThat(event.appId()).isEqualTo(TEST_APP_ID);
        assertThat(event.tenantId()).isEqualTo(TEST_TENANT_ID);
        assertThat(event.definitionId()).isEqualTo(defId);
    }

    // --- isNameAvailable ---

    @Test
    void shouldReturnTrueWhenNameIsAvailable() {
        AppEntity appEntity = createAppEntity();
        when(appRepository.findBySlugAndAuditInfoDeletedAtIsNull("my-app")).thenReturn(Optional.of(appEntity));
        when(permissionService.check(any(CheckPermissionRequest.class))).thenReturn(true);
        when(definitionRepository.existsByNameAndAppIdAndAuditInfoDeletedAtIsNull("Free Name", TEST_APP_ID))
                .thenReturn(false);

        assertThat(service.isNameAvailable("my-app", "Free Name")).isTrue();
    }

    @Test
    void shouldReturnFalseWhenNameIsTaken() {
        AppEntity appEntity = createAppEntity();
        when(appRepository.findBySlugAndAuditInfoDeletedAtIsNull("my-app")).thenReturn(Optional.of(appEntity));
        when(permissionService.check(any(CheckPermissionRequest.class))).thenReturn(true);
        when(definitionRepository.existsByNameAndAppIdAndAuditInfoDeletedAtIsNull("Taken Name", TEST_APP_ID))
                .thenReturn(true);

        assertThat(service.isNameAvailable("my-app", "Taken Name")).isFalse();
    }

    // --- updatePositions ---

    @Test
    void shouldBatchUpdatePositions() {
        UUID defId1 = UUID.randomUUID();
        UUID defId2 = UUID.randomUUID();

        ContentDefinitionEntity entity1 = createDefinitionEntity("Article", "article");
        entity1.setId(defId1);
        ContentDefinitionEntity entity2 = createDefinitionEntity("Author", "author");
        entity2.setId(defId2);

        AppEntity appEntity = createAppEntity();
        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(defId1)).thenReturn(Optional.of(entity1));
        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(defId2)).thenReturn(Optional.of(entity2));
        when(appRepository.findByIdAndAuditInfoDeletedAtIsNull(TEST_APP_ID)).thenReturn(Optional.of(appEntity));
        when(permissionService.check(any(CheckPermissionRequest.class))).thenReturn(true);

        ContentDefinition pos1 = new ContentDefinition();
        pos1.setId(defId1);
        pos1.setPositionX(100.0);
        pos1.setPositionY(200.0);

        ContentDefinition pos2 = new ContentDefinition();
        pos2.setId(defId2);
        pos2.setPositionX(300.0);
        pos2.setPositionY(400.0);

        service.updatePositions(List.of(pos1, pos2));

        assertThat(entity1.getPositionX()).isEqualTo(100.0);
        assertThat(entity1.getPositionY()).isEqualTo(200.0);
        assertThat(entity2.getPositionX()).isEqualTo(300.0);
        assertThat(entity2.getPositionY()).isEqualTo(400.0);
        verify(definitionRepository, times(2)).save(any(ContentDefinitionEntity.class));
    }

    // --- create: permission check ---

    @Test
    void shouldThrowAccessDeniedOnCreateWhenUserLacksEditorOnApp() {
        AppEntity appEntity = createAppEntity();
        when(appRepository.findBySlugAndAuditInfoDeletedAtIsNull("my-app")).thenReturn(Optional.of(appEntity));
        when(permissionService.check(argThat(r ->
                r != null && "editor".equals(r.action()) && "app".equals(r.resourceType())
                        && TEST_APP_ID.equals(r.resourceId()))))
                .thenReturn(false);

        ContentDefinition input = new ContentDefinition();
        input.setName("Article");
        assertThatThrownBy(() -> service.create("my-app", input))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(definitionRepository, never()).save(any());
    }

    // --- helper methods ---

    private AppEntity createAppEntity() {
        AppEntity entity = new AppEntity();
        entity.setId(TEST_APP_ID);
        entity.setTenantId(TEST_TENANT_ID);
        entity.setName("My App");
        entity.setSlug("my-app");
        entity.setStatus("ACTIVE");
        entity.setHasDraft(false);
        return entity;
    }

    private ContentDefinitionEntity createDefinitionEntity(String name, String slug) {
        ContentDefinitionEntity entity = new ContentDefinitionEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(TEST_TENANT_ID);
        entity.setAppId(TEST_APP_ID);
        entity.setName(name);
        entity.setSlug(slug);
        return entity;
    }
}

package io.webcontify.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.webcontify.app.mapper.AppMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.model.AppStatus;
import io.webcontify.app.repository.AppRepository;
import io.webcontify.app.repository.entity.AppEntity;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AppServiceTest {

    private static final UUID TEST_TENANT_ID = UUID.randomUUID();

    @Mock
    private AppRepository appRepository;

    @Mock
    private AppMapper appMapper;

    @Mock
    private SlugGenerator slugGenerator;

    @Mock
    private ChangeRecordService changeRecordService;

    private AppService appService;

    @BeforeEach
    void setUp() {
        TenantInformation info = new TenantInformation(TEST_TENANT_ID, false);
        var auth = new TenantAwareAuthentication(info, "test-user", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        appService = new AppService(appRepository, appMapper, slugGenerator, changeRecordService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCreateAppWithDraftStatusAndGeneratedSlug() {
        App input = new App();
        input.setName("My App");

        when(appRepository.existsByNameAndTenantIdAndAuditInfoDeletedAtIsNull("My App", TEST_TENANT_ID))
                .thenReturn(false);
        when(slugGenerator.generateUnique(eq("My App"), any(Predicate.class)))
                .thenReturn("my-app");

        AppEntity savedEntity = createAppEntity("My App", "my-app", "DRAFT");
        when(appMapper.toEntity(any(App.class))).thenReturn(savedEntity);
        when(appRepository.save(any(AppEntity.class))).thenReturn(savedEntity);

        App result = new App();
        result.setId(savedEntity.getId());
        result.setName("My App");
        result.setSlug("my-app");
        result.setStatus(AppStatus.DRAFT);
        result.setHasDraft(false);
        result.setTenantId(TEST_TENANT_ID);
        when(appMapper.toDomain(savedEntity)).thenReturn(result);

        App created = appService.create(input);

        assertThat(created.getName()).isEqualTo("My App");
        assertThat(created.getSlug()).isEqualTo("my-app");
        assertThat(created.getStatus()).isEqualTo(AppStatus.DRAFT);
        assertThat(created.isHasDraft()).isFalse();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getTenantId()).isEqualTo(TEST_TENANT_ID);

        verify(appRepository).save(any(AppEntity.class));
    }

    @Test
    void shouldThrowConflictOnDuplicateName() {
        App input = new App();
        input.setName("Existing App");

        when(appRepository.existsByNameAndTenantIdAndAuditInfoDeletedAtIsNull("Existing App", TEST_TENANT_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> appService.create(input))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("app.name_already_exists");
    }

    @Test
    void shouldReturnAppById() {
        UUID appId = UUID.randomUUID();
        AppEntity entity = createAppEntity("Test App", "test-app", "ACTIVE");
        entity.setId(appId);

        when(appRepository.findByIdAndAuditInfoDeletedAtIsNull(appId)).thenReturn(Optional.of(entity));

        App expected = new App();
        expected.setId(appId);
        expected.setName("Test App");
        when(appMapper.toDomain(entity)).thenReturn(expected);

        App result = appService.getById(appId);

        assertThat(result.getId()).isEqualTo(appId);
        assertThat(result.getName()).isEqualTo("Test App");
    }

    @Test
    void shouldThrowNotFoundForMissingApp() {
        UUID appId = UUID.randomUUID();

        when(appRepository.findByIdAndAuditInfoDeletedAtIsNull(appId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appService.getById(appId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("app.not_found");
    }

    @Test
    void shouldUpdateAppNameAndDescription() {
        UUID appId = UUID.randomUUID();
        AppEntity existingEntity = createAppEntity("Old Name", "old-name", "DRAFT");
        existingEntity.setId(appId);
        existingEntity.setTenantId(TEST_TENANT_ID);

        when(appRepository.findByIdAndAuditInfoDeletedAtIsNull(appId)).thenReturn(Optional.of(existingEntity));
        when(appRepository.existsByNameAndTenantIdAndAuditInfoDeletedAtIsNull("New Name", TEST_TENANT_ID))
                .thenReturn(false);

        AppEntity updatedEntity = createAppEntity("New Name", "old-name", "DRAFT");
        updatedEntity.setId(appId);
        updatedEntity.setDescription("New Description");
        when(appRepository.save(existingEntity)).thenReturn(updatedEntity);

        App updatedApp = new App();
        updatedApp.setId(appId);
        updatedApp.setName("New Name");
        updatedApp.setDescription("New Description");
        when(appMapper.toDomain(updatedEntity)).thenReturn(updatedApp);

        App update = new App();
        update.setName("New Name");
        update.setDescription("New Description");

        App result = appService.update(appId, update);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getDescription()).isEqualTo("New Description");
        verify(appMapper).updateEntity(existingEntity, update);
        verify(appRepository).save(existingEntity);
    }

    @Test
    void shouldActivateAppFromDraft() {
        UUID appId = UUID.randomUUID();
        AppEntity entity = createAppEntity("My App", "my-app", "DRAFT");
        entity.setId(appId);

        when(appRepository.findByIdAndAuditInfoDeletedAtIsNull(appId)).thenReturn(Optional.of(entity));

        AppEntity savedEntity = createAppEntity("My App", "my-app", "ACTIVE");
        savedEntity.setId(appId);
        when(appRepository.save(entity)).thenReturn(savedEntity);

        App activatedApp = new App();
        activatedApp.setId(appId);
        activatedApp.setStatus(AppStatus.ACTIVE);
        when(appMapper.toDomain(savedEntity)).thenReturn(activatedApp);

        App result = appService.updateStatus(appId, AppStatus.ACTIVE);

        assertThat(result.getStatus()).isEqualTo(AppStatus.ACTIVE);
        verify(appRepository).save(entity);
    }

    @Test
    void shouldDeactivateActiveApp() {
        UUID appId = UUID.randomUUID();
        AppEntity entity = createAppEntity("My App", "my-app", "ACTIVE");
        entity.setId(appId);

        when(appRepository.findByIdAndAuditInfoDeletedAtIsNull(appId)).thenReturn(Optional.of(entity));

        AppEntity savedEntity = createAppEntity("My App", "my-app", "INACTIVE");
        savedEntity.setId(appId);
        when(appRepository.save(entity)).thenReturn(savedEntity);

        App inactiveApp = new App();
        inactiveApp.setId(appId);
        inactiveApp.setStatus(AppStatus.INACTIVE);
        when(appMapper.toDomain(savedEntity)).thenReturn(inactiveApp);

        App result = appService.updateStatus(appId, AppStatus.INACTIVE);

        assertThat(result.getStatus()).isEqualTo(AppStatus.INACTIVE);
        verify(appRepository).save(entity);
    }

    @Test
    void shouldSoftDeleteApp() {
        UUID appId = UUID.randomUUID();
        AppEntity entity = createAppEntity("My App", "my-app", "DRAFT");
        entity.setId(appId);
        AuditInfo auditInfo = new AuditInfo();
        entity.setAuditInfo(auditInfo);

        when(appRepository.findByIdAndAuditInfoDeletedAtIsNull(appId)).thenReturn(Optional.of(entity));

        appService.delete(appId);

        assertThat(entity.getAuditInfo().getDeletedAt()).isNotNull();
        verify(appRepository).save(entity);
    }

    @Test
    void shouldCheckNameAvailability() {
        when(appRepository.existsByNameAndTenantIdAndAuditInfoDeletedAtIsNull("Available Name", TEST_TENANT_ID))
                .thenReturn(false);
        when(appRepository.existsByNameAndTenantIdAndAuditInfoDeletedAtIsNull("Taken Name", TEST_TENANT_ID))
                .thenReturn(true);

        assertThat(appService.isNameAvailable("Available Name")).isTrue();
        assertThat(appService.isNameAvailable("Taken Name")).isFalse();
    }

    @Test
    void shouldSetHasDraftWhenUpdatingActiveApp() {
        UUID appId = UUID.randomUUID();
        AppEntity existingEntity = createAppEntity("My App", "my-app", "ACTIVE");
        existingEntity.setId(appId);
        existingEntity.setTenantId(TEST_TENANT_ID);

        when(appRepository.findByIdAndAuditInfoDeletedAtIsNull(appId)).thenReturn(Optional.of(existingEntity));

        AppEntity savedEntity = createAppEntity("My App", "my-app", "ACTIVE");
        savedEntity.setId(appId);
        savedEntity.setHasDraft(true);
        when(appRepository.save(existingEntity)).thenReturn(savedEntity);

        App updatedApp = new App();
        updatedApp.setId(appId);
        updatedApp.setName("My App");
        updatedApp.setDescription("Updated description");
        updatedApp.setHasDraft(true);
        when(appMapper.toDomain(savedEntity)).thenReturn(updatedApp);

        App update = new App();
        update.setDescription("Updated description");

        App result = appService.update(appId, update);

        assertThat(existingEntity.isHasDraft()).isTrue();
        verify(appRepository).save(existingEntity);
    }

    @Test
    void shouldCreateRevisionWhenUpdatingApp() {
        UUID appId = UUID.randomUUID();
        AppEntity entity = new AppEntity();
        entity.setId(appId);
        entity.setName("Old Name");
        entity.setDescription("Old desc");
        entity.setSlug("old-name");
        entity.setStatus("DRAFT");
        entity.setTenantId(TEST_TENANT_ID);

        when(appRepository.findByIdAndAuditInfoDeletedAtIsNull(appId))
                .thenReturn(Optional.of(entity));
        when(appRepository.save(any(AppEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        App update = new App();
        update.setName("New Name");
        update.setDescription("New desc");

        // Simulate mapper updating the entity
        doAnswer(invocation -> {
            AppEntity e = invocation.getArgument(0);
            App u = invocation.getArgument(1);
            e.setName(u.getName());
            e.setDescription(u.getDescription());
            return null;
        }).when(appMapper).updateEntity(any(AppEntity.class), any(App.class));

        App result = new App();
        result.setId(appId);
        result.setName("New Name");
        result.setDescription("New desc");
        when(appMapper.toDomain(any(AppEntity.class))).thenReturn(result);

        when(appRepository.existsByNameAndTenantIdAndAuditInfoDeletedAtIsNull("New Name", TEST_TENANT_ID))
                .thenReturn(false);

        appService.update(appId, update);

        verify(changeRecordService).create(
                eq("app"), eq(appId), eq("updated"),
                argThat(patch -> patch.contains("replace")),
                argThat(source -> source.startsWith("user:")),
                eq("draft"));
    }

    private AppEntity createAppEntity(String name, String slug, String status) {
        AppEntity entity = new AppEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(TEST_TENANT_ID);
        entity.setName(name);
        entity.setSlug(slug);
        entity.setStatus(status);
        entity.setHasDraft(false);
        return entity;
    }
}

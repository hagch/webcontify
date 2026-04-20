package io.webcontify.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.util.SlugGenerator;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.model.AuditInfo;
import io.webcontify.shared.model.SystemUser;
import io.webcontify.tenant.event.TenantCreatedEvent;
import io.webcontify.tenant.mapper.TenantMapper;
import io.webcontify.tenant.mapper.TenantMapperImpl;
import io.webcontify.tenant.model.Tenant;
import io.webcontify.tenant.model.TenantStatus;
import io.webcontify.tenant.repository.TenantRepository;
import io.webcontify.tenant.repository.entity.TenantEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TenantMapper tenantMapper = new TenantMapperImpl();

    private SlugGenerator slugGenerator = new SlugGenerator();

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(tenantRepository, tenantMapper, slugGenerator, eventPublisher);
    }

    @Test
    void shouldCreateTenantWithGeneratedSlugAndProvisioningStatus() {
        Tenant input = new Tenant();
        input.setName("Test Tenant");

        when(tenantRepository.existsByNameAndAuditInfoDeletedAtIsNull("Test Tenant"))
                .thenReturn(false);
        when(tenantRepository.findBySlugAndAuditInfoDeletedAtIsNull("test-tenant"))
                .thenReturn(Optional.empty());
        when(tenantRepository.save(any(TenantEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Tenant result = tenantService.create(input, "admin@example.com", "Max", "Mustermann");

        assertThat(result.getId()).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Tenant");
        assertThat(result.getSlug()).isEqualTo("test-tenant");
        assertThat(result.getStatus()).isEqualTo(TenantStatus.PROVISIONING);
        assertThat(result.getProvisioningStatus()).isNotNull();
        assertThat(result.getProvisioningStatus()).contains("admin@example.com");
        verify(tenantRepository).save(any(TenantEntity.class));
    }

    @Test
    void shouldPublishTenantCreatedEventOnCreate() {
        Tenant input = new Tenant();
        input.setName("Event Tenant");

        when(tenantRepository.existsByNameAndAuditInfoDeletedAtIsNull("Event Tenant"))
                .thenReturn(false);
        when(tenantRepository.findBySlugAndAuditInfoDeletedAtIsNull("event-tenant"))
                .thenReturn(Optional.empty());
        when(tenantRepository.save(any(TenantEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        tenantService.create(input, "admin@example.com", "Max", "Mustermann");

        ArgumentCaptor<TenantCreatedEvent> eventCaptor = ArgumentCaptor.forClass(TenantCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TenantCreatedEvent event = eventCaptor.getValue();
        assertThat(event.tenantId()).isNotNull();
        assertThat(event.slug()).isEqualTo("event-tenant");
        assertThat(event.adminEmail()).isEqualTo("admin@example.com");
        assertThat(event.adminFirstName()).isEqualTo("Max");
        assertThat(event.adminLastName()).isEqualTo("Mustermann");
    }

    @Test
    void shouldThrowConflictOnDuplicateName() {
        Tenant input = new Tenant();
        input.setName("Existing Name");

        when(tenantRepository.existsByNameAndAuditInfoDeletedAtIsNull("Existing Name"))
                .thenReturn(true);

        assertThatThrownBy(() -> tenantService.create(input, "admin@example.com", "Max", "Mustermann"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("tenant.name_already_exists");
    }

    @Test
    void shouldReturnTrueWhenNameIsAvailable() {
        when(tenantRepository.existsByNameAndAuditInfoDeletedAtIsNull("Free Name"))
                .thenReturn(false);

        assertThat(tenantService.isNameAvailable("Free Name")).isTrue();
    }

    @Test
    void shouldReturnFalseWhenNameIsTaken() {
        when(tenantRepository.existsByNameAndAuditInfoDeletedAtIsNull("Taken Name"))
                .thenReturn(true);

        assertThat(tenantService.isNameAvailable("Taken Name")).isFalse();
    }

    @Test
    void shouldListTenants() {
        TenantEntity entity = createEntity();
        when(tenantRepository.findByAuditInfoDeletedAtIsNull(any()))
                .thenReturn(new PageImpl<>(List.of(entity)));

        var result = tenantService.list(PageRequest.of(0, 20), null);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void shouldListTenantsFilteredByStatus() {
        TenantEntity entity = createEntity();
        when(tenantRepository.findByStatusAndAuditInfoDeletedAtIsNull(eq("ACTIVE"), any()))
                .thenReturn(new PageImpl<>(List.of(entity)));

        var result = tenantService.list(PageRequest.of(0, 20), TenantStatus.ACTIVE);

        assertThat(result.getContent()).hasSize(1);
        verify(tenantRepository).findByStatusAndAuditInfoDeletedAtIsNull(eq("ACTIVE"), any());
    }

    @Test
    void shouldGetTenantById() {
        UUID id = UUID.randomUUID();
        TenantEntity entity = createEntity();
        entity.setId(id);
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(id))
                .thenReturn(Optional.of(entity));

        Tenant result = tenantService.getById(id);

        assertThat(result.getId()).isEqualTo(id);
    }

    @Test
    void shouldThrowNotFoundForMissingTenant() {
        UUID id = UUID.randomUUID();
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(id))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.getById(id))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("tenant.not_found");
    }

    @Test
    void shouldUpdateTenant() {
        UUID id = UUID.randomUUID();
        TenantEntity entity = createEntity();
        entity.setId(id);
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(id))
                .thenReturn(Optional.of(entity));
        when(tenantRepository.findBySlugAndAuditInfoDeletedAtIsNull("updated-slug"))
                .thenReturn(Optional.empty());
        when(tenantRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Tenant update = new Tenant();
        update.setName("Updated");
        update.setSlug("updated-slug");

        Tenant result = tenantService.update(id, update);

        assertThat(result.getName()).isEqualTo("Updated");
    }

    @Test
    void shouldThrowConflictOnUpdateWithDuplicateSlug() {
        UUID id = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        TenantEntity existing = createEntity();
        existing.setId(id);
        existing.setSlug("old-slug");

        TenantEntity other = createEntity();
        other.setId(otherId);
        other.setSlug("taken-slug");

        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(id))
                .thenReturn(Optional.of(existing));
        when(tenantRepository.findBySlugAndAuditInfoDeletedAtIsNull("taken-slug"))
                .thenReturn(Optional.of(other));

        Tenant update = new Tenant();
        update.setName("Updated");
        update.setSlug("taken-slug");

        assertThatThrownBy(() -> tenantService.update(id, update))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldUpdateTenantStatus() {
        UUID id = UUID.randomUUID();
        TenantEntity entity = createEntity();
        entity.setId(id);
        entity.setStatus("INACTIVE");
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(id))
                .thenReturn(Optional.of(entity));
        when(tenantRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Tenant result = tenantService.updateStatus(id, TenantStatus.ACTIVE);

        assertThat(result.getStatus()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void shouldSoftDeleteTenant() {
        UUID id = UUID.randomUUID();
        TenantEntity entity = createEntity();
        entity.setId(id);
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(id))
                .thenReturn(Optional.of(entity));

        tenantService.delete(id);

        verify(tenantRepository).save(argThat(e -> e.getAuditInfo().getDeletedAt() != null));
    }

    private TenantEntity createEntity() {
        TenantEntity entity = new TenantEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("Test");
        entity.setSlug("test");
        entity.setStatus("INACTIVE");

        AuditInfo auditInfo = new AuditInfo();
        auditInfo.setCreatedAt(OffsetDateTime.now());
        auditInfo.setCreatedBy(SystemUser.ID);
        auditInfo.setUpdatedAt(OffsetDateTime.now());
        auditInfo.setUpdatedBy(SystemUser.ID);
        entity.setAuditInfo(auditInfo);

        return entity;
    }
}

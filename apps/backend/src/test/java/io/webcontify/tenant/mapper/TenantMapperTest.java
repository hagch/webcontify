package io.webcontify.tenant.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.webcontify.generated.model.CreateTenantRequest;
import io.webcontify.generated.model.TenantResponse;
import io.webcontify.shared.model.AuditInfo;
import io.webcontify.tenant.model.Tenant;
import io.webcontify.tenant.model.TenantStatus;
import io.webcontify.tenant.repository.entity.TenantEntity;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantMapperTest {

    private final TenantMapper mapper = new TenantMapperImpl();

    @Test
    void shouldMapEntityToDomain() {
        TenantEntity entity = new TenantEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("Test");
        entity.setSlug("test");
        entity.setStatus("ACTIVE");

        AuditInfo auditInfo = new AuditInfo();
        auditInfo.setCreatedAt(OffsetDateTime.now());
        auditInfo.setCreatedBy(UUID.randomUUID());
        auditInfo.setUpdatedAt(OffsetDateTime.now());
        auditInfo.setUpdatedBy(UUID.randomUUID());
        entity.setAuditInfo(auditInfo);

        Tenant tenant = mapper.toDomain(entity);

        assertThat(tenant.getId()).isEqualTo(entity.getId());
        assertThat(tenant.getName()).isEqualTo("Test");
        assertThat(tenant.getSlug()).isEqualTo("test");
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.getCreatedAt()).isEqualTo(auditInfo.getCreatedAt());
        assertThat(tenant.getUpdatedAt()).isEqualTo(auditInfo.getUpdatedAt());
    }

    @Test
    void shouldMapDomainToEntity() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Test");
        tenant.setSlug("test");
        tenant.setStatus(TenantStatus.INACTIVE);

        TenantEntity entity = mapper.toEntity(tenant);

        assertThat(entity.getId()).isEqualTo(tenant.getId());
        assertThat(entity.getName()).isEqualTo("Test");
        assertThat(entity.getSlug()).isEqualTo("test");
        assertThat(entity.getStatus()).isEqualTo("INACTIVE");
    }

    @Test
    void shouldMapDomainToResponse() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Test");
        tenant.setSlug("test");
        tenant.setStatus(TenantStatus.SUSPENDED);
        tenant.setCreatedAt(OffsetDateTime.now());
        tenant.setUpdatedAt(OffsetDateTime.now());

        TenantResponse response = mapper.toResponse(tenant);

        assertThat(response.getId()).isEqualTo(tenant.getId());
        assertThat(response.getName()).isEqualTo("Test");
        assertThat(response.getSlug()).isEqualTo("test");
        assertThat(response.getStatus()).isEqualTo(io.webcontify.generated.model.TenantStatus.SUSPENDED);
        assertThat(response.getCreatedAt()).isEqualTo(tenant.getCreatedAt());
        assertThat(response.getUpdatedAt()).isEqualTo(tenant.getUpdatedAt());
    }

    @Test
    void shouldMapCreateRequestToDomain() {
        CreateTenantRequest request = new CreateTenantRequest("New Tenant", "new-tenant");

        Tenant tenant = mapper.toDomain(request);

        assertThat(tenant.getName()).isEqualTo("New Tenant");
        assertThat(tenant.getSlug()).isEqualTo("new-tenant");
    }

    @Test
    void shouldUpdateEntityFromDomain() {
        TenantEntity entity = new TenantEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("Old");
        entity.setSlug("old");
        entity.setStatus("INACTIVE");

        Tenant tenant = new Tenant();
        tenant.setName("Updated");
        tenant.setSlug("updated");
        tenant.setStatus(TenantStatus.ACTIVE);

        mapper.updateEntity(entity, tenant);

        assertThat(entity.getName()).isEqualTo("Updated");
        assertThat(entity.getSlug()).isEqualTo("updated");
        // Status is intentionally ignored by updateEntity — changed only via updateStatus endpoint
        assertThat(entity.getStatus()).isEqualTo("INACTIVE");
    }
}

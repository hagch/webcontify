package io.webcontify.tenant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.webcontify.shared.model.AuditInfo;
import io.webcontify.shared.model.SystemUser;
import io.webcontify.shared.util.UuidGenerator;
import io.webcontify.tenant.repository.entity.TenantEntity;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class TenantRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private TenantRepository tenantRepository;

    @Test
    void shouldSaveAndFindTenant() {
        TenantEntity entity = createTenantEntity("Test Tenant", "test-tenant");
        tenantRepository.save(entity);

        var found = tenantRepository.findById(entity.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test Tenant");
        assertThat(found.get().getSlug()).isEqualTo("test-tenant");
    }

    @Test
    void shouldFindBySlugAndDeletedAtIsNull() {
        TenantEntity entity = createTenantEntity("Slug Test", "slug-test");
        tenantRepository.save(entity);

        var found = tenantRepository.findBySlugAndAuditInfoDeletedAtIsNull("slug-test");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Slug Test");
    }

    @Test
    void shouldNotFindSoftDeletedBySlug() {
        TenantEntity entity = createTenantEntity("Deleted", "deleted-tenant");
        entity.getAuditInfo().setDeletedAt(OffsetDateTime.now());
        entity.getAuditInfo().setDeletedBy(SystemUser.ID);
        tenantRepository.save(entity);

        var found = tenantRepository.findBySlugAndAuditInfoDeletedAtIsNull("deleted-tenant");

        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindByIdAndDeletedAtIsNull() {
        TenantEntity entity = createTenantEntity("Active", "active-tenant");
        tenantRepository.save(entity);

        var found = tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(entity.getId());

        assertThat(found).isPresent();
    }

    @Test
    void shouldFilterByStatusWithPagination() {
        TenantEntity active = createTenantEntity("Active One", "active-one");
        active.setStatus("ACTIVE");
        tenantRepository.save(active);

        TenantEntity inactive = createTenantEntity("Inactive One", "inactive-one");
        inactive.setStatus("INACTIVE");
        tenantRepository.save(inactive);

        var page = tenantRepository.findByStatusAndAuditInfoDeletedAtIsNull(
                "ACTIVE", PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getSlug()).isEqualTo("active-one");
    }

    @Test
    void shouldPaginateAllNonDeletedTenants() {
        for (int i = 0; i < 3; i++) {
            tenantRepository.save(createTenantEntity("Tenant " + i, "tenant-" + i));
        }

        var page = tenantRepository.findByAuditInfoDeletedAtIsNull(PageRequest.of(0, 2));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    private TenantEntity createTenantEntity(String name, String slug) {
        TenantEntity entity = new TenantEntity();
        entity.setId(UuidGenerator.generate());
        entity.setName(name);
        entity.setSlug(slug);
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

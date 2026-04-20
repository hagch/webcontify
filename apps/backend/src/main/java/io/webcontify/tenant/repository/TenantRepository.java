package io.webcontify.tenant.repository;

import io.webcontify.tenant.repository.entity.TenantEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {

    Optional<TenantEntity> findBySlugAndAuditInfoDeletedAtIsNull(String slug);

    boolean existsByNameAndAuditInfoDeletedAtIsNull(String name);

    Optional<TenantEntity> findByIdAndAuditInfoDeletedAtIsNull(UUID id);

    Page<TenantEntity> findByAuditInfoDeletedAtIsNull(Pageable pageable);

    Page<TenantEntity> findByStatusAndAuditInfoDeletedAtIsNull(String status, Pageable pageable);
}

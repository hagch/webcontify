package io.webcontify.app.repository;

import io.webcontify.app.repository.entity.AppEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppRepository extends JpaRepository<AppEntity, UUID> {
    Optional<AppEntity> findByIdAndAuditInfoDeletedAtIsNull(UUID id);
    Optional<AppEntity> findBySlugAndAuditInfoDeletedAtIsNull(String slug);
    boolean existsByNameAndTenantIdAndAuditInfoDeletedAtIsNull(String name, UUID tenantId);
    boolean existsBySlugAndTenantIdAndAuditInfoDeletedAtIsNull(String slug, UUID tenantId);
    Page<AppEntity> findByAuditInfoDeletedAtIsNull(Pageable pageable);
    Page<AppEntity> findByStatusAndAuditInfoDeletedAtIsNull(String status, Pageable pageable);
}

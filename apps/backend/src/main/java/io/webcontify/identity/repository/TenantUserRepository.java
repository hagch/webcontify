package io.webcontify.identity.repository;

import io.webcontify.identity.model.UserStatus;
import io.webcontify.identity.repository.entity.TenantUserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface TenantUserRepository extends JpaRepository<TenantUserEntity, UUID> {

    @Query("SELECT u FROM TenantUserEntity u WHERE u.id = :id AND u.auditInfo.deletedAt IS NULL")
    Optional<TenantUserEntity> findActiveById(UUID id);

    @Query("SELECT u FROM TenantUserEntity u WHERE u.auditInfo.deletedAt IS NULL")
    Page<TenantUserEntity> findAllActive(Pageable pageable);

    @Query("SELECT u FROM TenantUserEntity u WHERE u.email = :email AND u.auditInfo.deletedAt IS NULL")
    Optional<TenantUserEntity> findActiveByEmail(String email);

    @Query("SELECT COUNT(u) FROM TenantUserEntity u WHERE u.status = :status AND u.auditInfo.deletedAt IS NULL")
    long countByStatus(UserStatus status);
}

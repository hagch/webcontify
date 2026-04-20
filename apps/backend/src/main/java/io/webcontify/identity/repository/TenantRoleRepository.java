package io.webcontify.identity.repository;

import io.webcontify.identity.repository.entity.TenantRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRoleRepository extends JpaRepository<TenantRoleEntity, UUID> {

    @Query("SELECT r FROM TenantRoleEntity r WHERE r.auditInfo.deletedAt IS NULL")
    List<TenantRoleEntity> findAllActive();

    @Query("SELECT r FROM TenantRoleEntity r WHERE r.id = :id AND r.auditInfo.deletedAt IS NULL")
    Optional<TenantRoleEntity> findActiveById(UUID id);

    @Query("SELECT r FROM TenantRoleEntity r WHERE r.name = :name AND r.auditInfo.deletedAt IS NULL")
    Optional<TenantRoleEntity> findActiveByName(String name);

    @Query("SELECT r FROM TenantRoleEntity r WHERE r.id IN :ids AND r.auditInfo.deletedAt IS NULL")
    List<TenantRoleEntity> findAllActiveByIds(List<UUID> ids);
}

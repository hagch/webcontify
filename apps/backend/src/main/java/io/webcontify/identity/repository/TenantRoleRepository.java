package io.webcontify.identity.repository;

import io.webcontify.identity.repository.entity.TenantRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TenantRoleRepository extends JpaRepository<TenantRoleEntity, UUID> {
}

package io.webcontify.identity.repository.entity;

import io.webcontify.shared.persistence.TenantAwareBaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "tenant_role")
public class TenantRoleEntity extends TenantAwareBaseEntity {

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "is_system_role", nullable = false)
    private boolean systemRole;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isSystemRole() { return systemRole; }
    public void setSystemRole(boolean systemRole) { this.systemRole = systemRole; }
}

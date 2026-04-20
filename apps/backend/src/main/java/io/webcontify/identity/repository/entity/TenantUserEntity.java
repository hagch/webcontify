package io.webcontify.identity.repository.entity;

import io.webcontify.identity.model.UserStatus;
import io.webcontify.shared.persistence.TenantAwareBaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "tenant_user")
public class TenantUserEntity extends TenantAwareBaseEntity {

    @Column(nullable = false)
    private String email;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false)
    @Convert(converter = UserStatusConverter.class)
    private UserStatus status;

    @Column(name = "invited_by")
    private UUID invitedBy;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public UUID getInvitedBy() { return invitedBy; }
    public void setInvitedBy(UUID invitedBy) { this.invitedBy = invitedBy; }
}

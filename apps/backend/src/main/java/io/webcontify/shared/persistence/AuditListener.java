package io.webcontify.shared.persistence;

import io.webcontify.shared.model.AuditInfo;
import io.webcontify.shared.model.SystemUser;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.OffsetDateTime;
import java.util.UUID;

public class AuditListener {

    @PrePersist
    public void prePersist(AuditableBaseEntity entity) {
        OffsetDateTime now = OffsetDateTime.now();
        UUID userId = resolveCurrentUser();

        AuditInfo auditInfo = entity.getAuditInfo();
        if (auditInfo == null) {
            auditInfo = new AuditInfo();
            entity.setAuditInfo(auditInfo);
        }

        auditInfo.setCreatedAt(now);
        auditInfo.setCreatedBy(userId);
        auditInfo.setUpdatedAt(now);
        auditInfo.setUpdatedBy(userId);
    }

    @PreUpdate
    public void preUpdate(AuditableBaseEntity entity) {
        OffsetDateTime now = OffsetDateTime.now();
        UUID userId = resolveCurrentUser();

        AuditInfo auditInfo = entity.getAuditInfo();
        auditInfo.setUpdatedAt(now);
        auditInfo.setUpdatedBy(userId);
    }

    private UUID resolveCurrentUser() {
        // TODO: Resolve from SecurityContext when Keycloak is integrated (Phase 2)
        return SystemUser.ID;
    }
}

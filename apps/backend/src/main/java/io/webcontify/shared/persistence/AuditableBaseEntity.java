package io.webcontify.shared.persistence;

import io.webcontify.shared.model.AuditInfo;
import jakarta.persistence.*;
import java.util.UUID;

@MappedSuperclass
@EntityListeners(AuditListener.class)
public abstract class AuditableBaseEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Embedded private AuditInfo auditInfo;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public AuditInfo getAuditInfo() {
        return auditInfo;
    }

    public void setAuditInfo(AuditInfo auditInfo) {
        this.auditInfo = auditInfo;
    }
}

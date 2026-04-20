package io.webcontify.content.management.ddl.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "content_field_change_log")
public class ContentFieldChangeLogEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "definition_id", nullable = false)
    private UUID definitionId;

    @Column(name = "field_id", nullable = false)
    private UUID fieldId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "old_type")
    private String oldType;

    @Column(name = "new_type")
    private String newType;

    @Column(name = "data_loss", nullable = false)
    private boolean dataLoss;

    @Column(name = "executed_at", nullable = false)
    private OffsetDateTime executedAt;

    @Column(name = "executed_by", nullable = false)
    private UUID executedBy;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getDefinitionId() { return definitionId; }
    public void setDefinitionId(UUID definitionId) { this.definitionId = definitionId; }
    public UUID getFieldId() { return fieldId; }
    public void setFieldId(UUID fieldId) { this.fieldId = fieldId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getOldType() { return oldType; }
    public void setOldType(String oldType) { this.oldType = oldType; }
    public String getNewType() { return newType; }
    public void setNewType(String newType) { this.newType = newType; }
    public boolean isDataLoss() { return dataLoss; }
    public void setDataLoss(boolean dataLoss) { this.dataLoss = dataLoss; }
    public OffsetDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(OffsetDateTime executedAt) { this.executedAt = executedAt; }
    public UUID getExecutedBy() { return executedBy; }
    public void setExecutedBy(UUID executedBy) { this.executedBy = executedBy; }
}

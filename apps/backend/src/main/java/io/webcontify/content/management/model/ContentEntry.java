package io.webcontify.content.management.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public class ContentEntry {

    private UUID id;
    private UUID definitionId;
    private long version;
    private Map<String, Object> values;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;

    public ContentEntry() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getDefinitionId() { return definitionId; }
    public void setDefinitionId(UUID definitionId) { this.definitionId = definitionId; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public Map<String, Object> getValues() { return values; }
    public void setValues(Map<String, Object> values) { this.values = values; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
}

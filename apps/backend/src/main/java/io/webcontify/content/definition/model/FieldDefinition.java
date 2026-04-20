package io.webcontify.content.definition.model;

import java.util.UUID;

public class FieldDefinition {
    private UUID id;
    private UUID tenantId;
    private UUID definitionId;
    private String fieldKey;
    private FieldType fieldType;
    private boolean required;
    private boolean uniqueValue;
    private boolean sortable;
    private boolean filterable;
    private FieldConfig config;
    private int position;

    public FieldDefinition() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getDefinitionId() { return definitionId; }
    public void setDefinitionId(UUID definitionId) { this.definitionId = definitionId; }
    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }
    public FieldType getFieldType() { return fieldType; }
    public void setFieldType(FieldType fieldType) { this.fieldType = fieldType; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public boolean isUniqueValue() { return uniqueValue; }
    public void setUniqueValue(boolean uniqueValue) { this.uniqueValue = uniqueValue; }
    public boolean isSortable() { return sortable; }
    public void setSortable(boolean sortable) { this.sortable = sortable; }
    public boolean isFilterable() { return filterable; }
    public void setFilterable(boolean filterable) { this.filterable = filterable; }
    public FieldConfig getConfig() { return config; }
    public void setConfig(FieldConfig config) { this.config = config; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}

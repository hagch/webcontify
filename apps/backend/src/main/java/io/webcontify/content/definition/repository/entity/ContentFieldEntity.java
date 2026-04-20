package io.webcontify.content.definition.repository.entity;

import io.webcontify.shared.persistence.TenantAwareBaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "content_field_definition")
public class ContentFieldEntity extends TenantAwareBaseEntity {

    @Column(name = "definition_id", nullable = false, insertable = false, updatable = false)
    private UUID definitionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id", nullable = false)
    private ContentDefinitionEntity definition;

    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(name = "field_type", nullable = false)
    private String fieldType;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "unique_value", nullable = false)
    private boolean uniqueValue;

    @Column(name = "sortable", nullable = false)
    private boolean sortable;

    @Column(name = "filterable", nullable = false)
    private boolean filterable;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private String config;

    @Column(name = "position", nullable = false)
    private int position;

    public UUID getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(UUID definitionId) {
        this.definitionId = definitionId;
    }

    public ContentDefinitionEntity getDefinition() {
        return definition;
    }

    public void setDefinition(ContentDefinitionEntity definition) {
        this.definition = definition;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isUniqueValue() {
        return uniqueValue;
    }

    public void setUniqueValue(boolean uniqueValue) {
        this.uniqueValue = uniqueValue;
    }

    public boolean isSortable() {
        return sortable;
    }

    public void setSortable(boolean sortable) {
        this.sortable = sortable;
    }

    public boolean isFilterable() {
        return filterable;
    }

    public void setFilterable(boolean filterable) {
        this.filterable = filterable;
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}

package io.webcontify.content.fieldtype.handler;

import io.webcontify.content.definition.model.FieldConfig;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.model.MirrorFieldConfig;
import io.webcontify.content.fieldtype.FieldTypeHandler;
import io.webcontify.content.fieldtype.Operator;
import io.webcontify.shared.exception.FieldError;
import io.webcontify.shared.exception.ValidationException;
import org.jooq.DataType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class MirrorFieldTypeHandler implements FieldTypeHandler {

    @Override
    public FieldType fieldType() {
        return FieldType.MIRROR;
    }

    @Override
    public boolean isVirtual() {
        return true;
    }

    @Override
    public void validateConfig(FieldConfig config) {
        if (config == null) return;
        var c = (MirrorFieldConfig) config;

        if (c.sourceDefinitionId() == null) {
            throw validationError("config.sourceDefinitionId", "error.config.sourceDefinitionId.required");
        }
        if (c.sourceFieldId() == null) {
            throw validationError("config.sourceFieldId", "error.config.sourceFieldId.required");
        }
        if (c.ownerAppId() == null) {
            throw validationError("config.ownerAppId", "error.config.ownerAppId.required");
        }
    }

    @Override
    public Object cast(Object rawValue, FieldConfig config) {
        return null;
    }

    @Override
    public void validateValue(Object value, FieldConfig config) {
        throw validationError("value", "backendError.mirror_not_writable");
    }

    @Override
    public DataType<?> columnType(FieldConfig config) {
        throw new UnsupportedOperationException("Mirror fields have no column");
    }

    @Override
    public Set<Operator> supportedOperators() {
        return Set.of();
    }

    private ValidationException validationError(String field, String messageKey) {
        return new ValidationException("VALIDATION_ERROR", List.of(new FieldError(field, messageKey)));
    }
}

package io.webcontify.content.definition.service;

import io.webcontify.content.definition.model.*;
import io.webcontify.shared.exception.FieldError;
import io.webcontify.shared.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
public class ContentFieldConfigValidator {

    public void validate(FieldType fieldType, FieldConfig config) {
        if (config == null) {
            return;
        }

        switch (config) {
            case TextFieldConfig c -> validateText(c);
            case IntegerFieldConfig c -> validateInteger(c);
            case DecimalFieldConfig c -> validateDecimal(c);
            case BooleanFieldConfig c -> { /* no constraints to validate */ }
            case DateFieldConfig c -> validateDate(c);
            case EnumFieldConfig c -> validateEnum(c);
            case ReferenceFieldConfig c -> validateReference(c);
            case MirrorFieldConfig c -> validateMirror(c);
        }
    }

    private void validateText(TextFieldConfig config) {
        if (config.minLength() != null && config.maxLength() != null && config.minLength() > config.maxLength()) {
            throw validationError("config.minLength", "error.config.minLength.greaterThanMaxLength");
        }
        if (config.pattern() != null) {
            try {
                Pattern.compile(config.pattern());
            } catch (PatternSyntaxException e) {
                throw validationError("config.pattern", "error.config.pattern.invalidRegex");
            }
        }
        if (config.defaultValue() != null) {
            if (config.minLength() != null && config.defaultValue().length() < config.minLength()) {
                throw validationError("config.defaultValue", "error.config.defaultValue.belowMinLength");
            }
            if (config.maxLength() != null && config.defaultValue().length() > config.maxLength()) {
                throw validationError("config.defaultValue", "error.config.defaultValue.exceedsMaxLength");
            }
        }
    }

    private void validateInteger(IntegerFieldConfig config) {
        if (config.min() != null && config.max() != null && config.min() > config.max()) {
            throw validationError("config.min", "error.config.min.greaterThanMax");
        }
        if (config.defaultValue() != null) {
            if (config.min() != null && config.defaultValue() < config.min()) {
                throw validationError("config.defaultValue", "error.config.defaultValue.belowMin");
            }
            if (config.max() != null && config.defaultValue() > config.max()) {
                throw validationError("config.defaultValue", "error.config.defaultValue.exceedsMax");
            }
        }
    }

    private void validateDecimal(DecimalFieldConfig config) {
        if (config.min() != null && config.max() != null && config.min().compareTo(config.max()) > 0) {
            throw validationError("config.min", "error.config.min.greaterThanMax");
        }
        if (config.precision() != null && config.scale() != null && config.precision() < config.scale()) {
            throw validationError("config.precision", "error.config.precision.lessThanScale");
        }
    }

    private void validateDate(DateFieldConfig config) {
        if (config.minDate() != null && config.maxDate() != null && config.minDate().isAfter(config.maxDate())) {
            throw validationError("config.minDate", "error.config.minDate.afterMaxDate");
        }
    }

    private void validateEnum(EnumFieldConfig config) {
        if (config.allowedValues() == null || config.allowedValues().isEmpty()) {
            throw validationError("config.allowedValues", "error.config.allowedValues.empty");
        }
        if (config.defaultValue() != null && !config.allowedValues().contains(config.defaultValue())) {
            throw validationError("config.defaultValue", "error.config.defaultValue.notInAllowedValues");
        }
    }

    private void validateReference(ReferenceFieldConfig config) {
        if (config.targetDefinitionId() == null) {
            throw validationError("config.targetDefinitionId", "error.config.targetDefinitionId.required");
        }
        if (config.relationType() == null) {
            throw validationError("config.relationType", "error.config.relationType.required");
        }
        if (config.onDelete() == null) {
            throw validationError("config.onDelete", "error.config.onDelete.required");
        }
    }

    private void validateMirror(MirrorFieldConfig config) {
        if (config.sourceDefinitionId() == null) {
            throw validationError("config.sourceDefinitionId", "error.config.sourceDefinitionId.required");
        }
        if (config.sourceFieldId() == null) {
            throw validationError("config.sourceFieldId", "error.config.sourceFieldId.required");
        }
        if (config.ownerAppId() == null) {
            throw validationError("config.ownerAppId", "error.config.ownerAppId.required");
        }
    }

    private ValidationException validationError(String field, String messageKey) {
        return new ValidationException("VALIDATION_ERROR", List.of(new FieldError(field, messageKey)));
    }
}

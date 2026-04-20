package io.webcontify.content.definition.service;

import io.webcontify.app.repository.AppDependencyRepository;
import io.webcontify.content.definition.model.ReferenceFieldConfig;
import io.webcontify.content.definition.repository.ContentDefinitionRepository;
import io.webcontify.shared.exception.FieldError;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ContentReferenceValidator {

    private static final Pattern DISPLAY_FORMAT_PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    private final ContentDefinitionRepository definitionRepository;
    private final AppDependencyRepository dependencyRepository;

    public ContentReferenceValidator(
            ContentDefinitionRepository definitionRepository,
            AppDependencyRepository dependencyRepository) {
        this.definitionRepository = definitionRepository;
        this.dependencyRepository = dependencyRepository;
    }

    public void validateReference(UUID sourceAppId, ReferenceFieldConfig config) {
        var targetDefinition = definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(config.targetDefinitionId())
                .orElseThrow(() -> new NotFoundException("error.contentDefinition.notFound"));

        UUID targetAppId = targetDefinition.getAppId();

        if (!sourceAppId.equals(targetAppId)) {
            boolean hasDependency = dependencyRepository.existsByAppIdAndDependsOnId(sourceAppId, targetAppId);
            if (!hasDependency) {
                throw new ValidationException(
                        "VALIDATION_ERROR",
                        List.of(new FieldError("config.targetDefinitionId", "error.reference.crossAppDependencyMissing")));
            }
        }
    }

    public void validateDisplayFormat(String displayFormat, List<String> existingFieldKeys) {
        if (displayFormat == null || displayFormat.isBlank()) {
            return;
        }

        Matcher matcher = DISPLAY_FORMAT_PLACEHOLDER.matcher(displayFormat);
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            if (!existingFieldKeys.contains(placeholder)) {
                throw new ValidationException(
                        "VALIDATION_ERROR",
                        List.of(new FieldError("displayFormat", "error.displayFormat.unknownPlaceholder")));
            }
        }
    }
}

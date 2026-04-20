package io.webcontify.content.definition.service;

import io.webcontify.app.repository.AppDependencyRepository;
import io.webcontify.content.definition.model.OnDeleteStrategy;
import io.webcontify.content.definition.model.ReferenceFieldConfig;
import io.webcontify.content.definition.model.RelationType;
import io.webcontify.content.definition.repository.ContentDefinitionRepository;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentReferenceValidatorTest {

    private static final UUID SOURCE_APP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_APP_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TARGET_DEF_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock
    private ContentDefinitionRepository definitionRepository;

    @Mock
    private AppDependencyRepository dependencyRepository;

    @InjectMocks
    private ContentReferenceValidator validator;

    @Test
    void validateReference_sameApp_valid() {
        var targetEntity = new ContentDefinitionEntity();
        targetEntity.setAppId(SOURCE_APP_ID);
        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(TARGET_DEF_ID))
                .thenReturn(Optional.of(targetEntity));

        var config = new ReferenceFieldConfig(TARGET_DEF_ID, RelationType.MANY_TO_ONE, OnDeleteStrategy.RESTRICT);

        assertThatNoException().isThrownBy(() -> validator.validateReference(SOURCE_APP_ID, config));
        verify(dependencyRepository, never()).existsByAppIdAndDependsOnId(any(), any());
    }

    @Test
    void validateReference_crossAppWithDependency_valid() {
        var targetEntity = new ContentDefinitionEntity();
        targetEntity.setAppId(TARGET_APP_ID);
        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(TARGET_DEF_ID))
                .thenReturn(Optional.of(targetEntity));
        when(dependencyRepository.existsByAppIdAndDependsOnId(SOURCE_APP_ID, TARGET_APP_ID))
                .thenReturn(true);

        var config = new ReferenceFieldConfig(TARGET_DEF_ID, RelationType.MANY_TO_ONE, OnDeleteStrategy.RESTRICT);

        assertThatNoException().isThrownBy(() -> validator.validateReference(SOURCE_APP_ID, config));
    }

    @Test
    void validateReference_crossAppWithoutDependency_throws() {
        var targetEntity = new ContentDefinitionEntity();
        targetEntity.setAppId(TARGET_APP_ID);
        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(TARGET_DEF_ID))
                .thenReturn(Optional.of(targetEntity));
        when(dependencyRepository.existsByAppIdAndDependsOnId(SOURCE_APP_ID, TARGET_APP_ID))
                .thenReturn(false);

        var config = new ReferenceFieldConfig(TARGET_DEF_ID, RelationType.MANY_TO_ONE, OnDeleteStrategy.RESTRICT);

        assertThatThrownBy(() -> validator.validateReference(SOURCE_APP_ID, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateReference_targetDefinitionNotFound_throws() {
        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(TARGET_DEF_ID))
                .thenReturn(Optional.empty());

        var config = new ReferenceFieldConfig(TARGET_DEF_ID, RelationType.MANY_TO_ONE, OnDeleteStrategy.RESTRICT);

        assertThatThrownBy(() -> validator.validateReference(SOURCE_APP_ID, config))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void validateDisplayFormat_validPlaceholders_passes() {
        assertThatNoException().isThrownBy(
                () -> validator.validateDisplayFormat("{name} - {title}", List.of("name", "title", "description")));
    }

    @Test
    void validateDisplayFormat_unknownPlaceholder_throws() {
        assertThatThrownBy(
                () -> validator.validateDisplayFormat("{name} - {unknown}", List.of("name", "title")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateDisplayFormat_nullOrBlank_passes() {
        assertThatNoException().isThrownBy(
                () -> validator.validateDisplayFormat(null, List.of("name")));
        assertThatNoException().isThrownBy(
                () -> validator.validateDisplayFormat("  ", List.of("name")));
    }
}

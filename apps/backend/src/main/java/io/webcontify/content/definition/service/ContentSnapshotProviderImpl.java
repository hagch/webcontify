package io.webcontify.content.definition.service;

import io.webcontify.app.model.AppSnapshot;
import io.webcontify.app.service.ContentSnapshotProvider;
import io.webcontify.content.definition.repository.ContentDefinitionRepository;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentSnapshotProviderImpl implements ContentSnapshotProvider {

    private final ContentDefinitionRepository contentDefinitionRepository;

    public ContentSnapshotProviderImpl(ContentDefinitionRepository contentDefinitionRepository) {
        this.contentDefinitionRepository = contentDefinitionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppSnapshot.AppSnapshotDefinition> getSnapshotDefinitions(UUID appId) {
        List<ContentDefinitionEntity> definitions =
                contentDefinitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(appId);

        return definitions.stream().map(this::toSnapshotDefinition).toList();
    }

    private AppSnapshot.AppSnapshotDefinition toSnapshotDefinition(
            ContentDefinitionEntity entity) {
        List<AppSnapshot.AppSnapshotField> snapshotFields =
                entity.getFields().stream()
                        .filter(f -> f.getAuditInfo() == null || f.getAuditInfo().getDeletedAt() == null)
                        .map(this::toSnapshotField)
                        .toList();

        return new AppSnapshot.AppSnapshotDefinition(
                entity.getId().toString(),
                entity.getName(),
                entity.getSlug(),
                entity.getDescription(),
                entity.getDisplayFormat(),
                snapshotFields);
    }

    private AppSnapshot.AppSnapshotField toSnapshotField(ContentFieldEntity field) {
        return new AppSnapshot.AppSnapshotField(
                field.getId().toString(),
                field.getFieldKey(),
                field.getFieldType(),
                field.isRequired(),
                field.isUniqueValue(),
                field.isSortable(),
                field.isFilterable(),
                field.getConfig(),
                field.getPosition());
    }
}

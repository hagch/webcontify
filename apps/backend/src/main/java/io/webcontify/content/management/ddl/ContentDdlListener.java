package io.webcontify.content.management.ddl;

import io.webcontify.app.event.AppPublishedEvent;
import io.webcontify.content.definition.repository.ContentDefinitionRepository;
import io.webcontify.content.definition.repository.ContentFieldRepository;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import io.webcontify.content.management.ddl.repository.ContentFieldChangeLogRepository;
import io.webcontify.content.management.ddl.repository.entity.ContentFieldChangeLogEntity;
import io.webcontify.shared.event.TenantAwareModuleListener;
import io.webcontify.shared.persistence.TenantSlugCache;
import io.webcontify.shared.util.UuidGenerator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Listens for {@link AppPublishedEvent} and applies pending DDL changes
 * (table creation, column additions/removals) for all content definitions
 * belonging to the published app.
 *
 * <p>Uses {@link TenantAwareModuleListener} which sets the tenant SecurityContext
 * before the transaction opens, ensuring RLS-enabled JPA queries work correctly.
 */
@Component
public class ContentDdlListener {

    private static final Logger log = LoggerFactory.getLogger(ContentDdlListener.class);

    private final ContentDefinitionRepository definitionRepository;
    private final ContentFieldRepository fieldRepository;
    private final DdlDiffEngine ddlDiffEngine;
    private final DynamicTableManager dynamicTableManager;
    private final ContentFieldChangeLogRepository changeLogRepository;
    private final TenantSlugCache tenantSlugCache;

    public ContentDdlListener(
            ContentDefinitionRepository definitionRepository,
            ContentFieldRepository fieldRepository,
            DdlDiffEngine ddlDiffEngine,
            DynamicTableManager dynamicTableManager,
            ContentFieldChangeLogRepository changeLogRepository,
            TenantSlugCache tenantSlugCache) {
        this.definitionRepository = definitionRepository;
        this.fieldRepository = fieldRepository;
        this.ddlDiffEngine = ddlDiffEngine;
        this.dynamicTableManager = dynamicTableManager;
        this.changeLogRepository = changeLogRepository;
        this.tenantSlugCache = tenantSlugCache;
    }

    @TenantAwareModuleListener
    public void on(AppPublishedEvent event) {
        UUID tenantId = event.tenantId();
        UUID appId = event.appId();

        String slug = tenantSlugCache.resolveSlug(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot resolve slug for tenant " + tenantId));
        String schemaName = "tenant_" + slug.replace("-", "_");

        log.info("Processing DDL changes for app {} in schema {}", appId, schemaName);

        List<ContentDefinitionEntity> definitions =
                definitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(appId);

        for (ContentDefinitionEntity definition : definitions) {
            processDdlForDefinition(definition, schemaName, tenantId);
        }

        log.info("DDL changes completed for app {} in schema {}", appId, schemaName);
    }

    private void processDdlForDefinition(
            ContentDefinitionEntity definition, String schemaName, UUID tenantId) {

        List<ContentFieldEntity> pendingFields =
                fieldRepository.findByDefinitionIdAndPendingActionIsNotNull(definition.getId());

        if (pendingFields.isEmpty()) {
            log.debug("No pending DDL changes for definition {}", definition.getSlug());
            return;
        }

        boolean isNewTable = definition.getFields().stream()
                .noneMatch(ContentFieldEntity::isPublished);

        List<DdlChange> changes = ddlDiffEngine.diff(isNewTable, pendingFields);

        if (changes.isEmpty()) {
            return;
        }

        log.info("Applying {} DDL changes for definition {}", changes.size(), definition.getSlug());

        if (isNewTable) {
            dynamicTableManager.createTable(schemaName, definition);
        } else {
            dynamicTableManager.applyChanges(schemaName, definition, changes);
        }

        for (ContentFieldEntity field : pendingFields) {
            String pendingAction = field.getPendingAction();
            String oldType = "REMOVED".equals(pendingAction) ? field.getFieldType() : null;
            String newType = "ADDED".equals(pendingAction) ? field.getFieldType() : null;
            boolean dataLoss = "REMOVED".equals(pendingAction) || "MODIFIED".equals(pendingAction);

            var changeLog = new ContentFieldChangeLogEntity();
            changeLog.setId(UuidGenerator.generate());
            changeLog.setTenantId(tenantId);
            changeLog.setDefinitionId(definition.getId());
            changeLog.setFieldId(field.getId());
            changeLog.setAction(pendingAction);
            changeLog.setOldType(oldType);
            changeLog.setNewType(newType);
            changeLog.setDataLoss(dataLoss);
            changeLog.setExecutedAt(OffsetDateTime.now());
            changeLog.setExecutedBy(tenantId);
            changeLogRepository.save(changeLog);

            field.setPublished(!"REMOVED".equals(pendingAction));
            field.setPendingAction(null);
            fieldRepository.save(field);
        }
    }
}

package io.webcontify.content.management.storage;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.model.EntryHistoryRecord;
import io.webcontify.content.management.model.EntryReference;
import io.webcontify.content.management.query.ContentQuery;
import io.webcontify.content.management.query.CursorPage;
import io.webcontify.content.management.query.CursorPageRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage abstraction for content entries in per-definition dedicated tables.
 * All read methods enforce soft-delete filtering (WHERE deleted_at IS NULL).
 */
public interface ContentStorage {

    ContentEntry save(String schemaName, ContentDefinition def, Map<String, Object> values, UUID createdBy);

    ContentEntry update(String schemaName, ContentDefinition def, UUID entryId, long expectedVersion,
                        Map<String, Object> values, UUID updatedBy);

    void softDelete(String schemaName, ContentDefinition def, UUID entryId, UUID deletedBy);

    Optional<ContentEntry> findById(String schemaName, ContentDefinition def, UUID entryId);

    List<ContentEntry> findAll(String schemaName, ContentDefinition def, int limit);

    List<ContentEntry> findByReferenceField(String schemaName, ContentDefinition def, String fieldKey, UUID targetEntryId);

    boolean existsByFieldValue(String schemaName, ContentDefinition def, String fieldKey, Object value, UUID excludeEntryId);

    void nullifyField(String schemaName, ContentDefinition def, String fieldKey, UUID targetEntryId, UUID updatedBy);

    List<EntryHistoryRecord> getHistory(String schemaName, ContentDefinition def, UUID entryId, int limit);

    CursorPage<ContentEntry> query(String schemaName, ContentDefinition def, ContentQuery query);

    CursorPage<EntryReference> search(String schemaName, ContentDefinition def, String searchTerm, CursorPageRequest page);
}

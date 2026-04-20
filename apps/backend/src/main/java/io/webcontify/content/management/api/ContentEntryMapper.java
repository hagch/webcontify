package io.webcontify.content.management.api;

import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.model.EntryHistoryRecord;
import io.webcontify.content.management.model.EntryReference;
import io.webcontify.content.management.query.CursorPage;
import io.webcontify.generated.model.*;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Manual mapper between domain objects and generated OpenAPI DTOs for content entries.
 * MapStruct is not used here because the main payload is a dynamic Map&lt;String, Object&gt;.
 */
@Component
public class ContentEntryMapper {

    public ContentEntryResponse toResponse(ContentEntry entry) {
        return toResponse(entry, Map.of());
    }

    public ContentEntryResponse toResponse(ContentEntry entry, Map<UUID, String> displayNames) {
        ContentEntryResponse response = new ContentEntryResponse();
        response.setId(entry.getId());
        response.setVersion(entry.getVersion());
        response.setValues(entry.getValues() != null ? new LinkedHashMap<>(entry.getValues()) : Map.of());
        response.setCreatedAt(entry.getCreatedAt());
        response.setUpdatedAt(entry.getUpdatedAt());
        response.setCreatedBy(entry.getCreatedBy());
        response.setUpdatedBy(entry.getUpdatedBy());
        response.setCreatedByName(entry.getCreatedBy() != null ? displayNames.get(entry.getCreatedBy()) : null);
        response.setUpdatedByName(entry.getUpdatedBy() != null ? displayNames.get(entry.getUpdatedBy()) : null);
        return response;
    }

    public ContentEntryListResponse toListResponse(CursorPage<ContentEntry> page, Map<UUID, String> displayNames) {
        ContentEntryListResponse response = new ContentEntryListResponse();
        response.setItems(page.items().stream().map(e -> toResponse(e, displayNames)).toList());
        response.setNextCursor(page.nextCursor());
        response.setHasNext(page.hasNext());
        return response;
    }

    public EntryReferenceResponse toReferenceResponse(EntryReference ref) {
        EntryReferenceResponse response = new EntryReferenceResponse();
        response.setId(ref.id());
        response.setDisplay(ref.display());
        return response;
    }

    public EntryReferenceListResponse toReferenceListResponse(CursorPage<EntryReference> page) {
        EntryReferenceListResponse response = new EntryReferenceListResponse();
        response.setItems(page.items().stream().map(this::toReferenceResponse).toList());
        response.setNextCursor(page.nextCursor());
        response.setHasNext(page.hasNext());
        return response;
    }

    public EntryHistoryResponse toHistoryResponse(EntryHistoryRecord record, Map<UUID, String> displayNames) {
        EntryHistoryResponse response = new EntryHistoryResponse();
        response.setId(record.id());
        response.setEntryId(record.entryId());
        response.setAction(record.action());
        if (record.changedFields() != null) {
            Map<String, Object> changedFieldsMap = new LinkedHashMap<>();
            for (var entry : record.changedFields().entrySet()) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("old", entry.getValue().oldValue());
                change.put("new", entry.getValue().newValue());
                changedFieldsMap.put(entry.getKey(), change);
            }
            response.setChangedFields(changedFieldsMap);
        }
        response.setChangedAt(record.changedAt());
        response.setChangedBy(record.changedBy());
        response.setChangedByName(record.changedBy() != null ? displayNames.get(record.changedBy()) : null);
        return response;
    }

    public EntryHistoryListResponse toHistoryListResponse(CursorPage<EntryHistoryRecord> page, Map<UUID, String> displayNames) {
        EntryHistoryListResponse response = new EntryHistoryListResponse();
        response.setItems(page.items().stream().map(r -> toHistoryResponse(r, displayNames)).toList());
        response.setNextCursor(page.nextCursor());
        response.setHasNext(page.hasNext());
        return response;
    }
}

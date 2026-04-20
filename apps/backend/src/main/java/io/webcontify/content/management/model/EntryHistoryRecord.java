package io.webcontify.content.management.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record EntryHistoryRecord(
        UUID id,
        UUID entryId,
        String action,
        Map<String, FieldChange> changedFields,
        OffsetDateTime changedAt,
        UUID changedBy) {

    public record FieldChange(Object oldValue, Object newValue) {}
}

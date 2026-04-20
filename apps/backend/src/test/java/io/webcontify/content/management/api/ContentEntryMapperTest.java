package io.webcontify.content.management.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.model.EntryHistoryRecord;
import io.webcontify.content.management.model.EntryReference;
import io.webcontify.content.management.query.CursorPage;
import io.webcontify.generated.model.*;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContentEntryMapperTest {

    private final ContentEntryMapper mapper = new ContentEntryMapper();

    @Test
    void toResponse_mapsAllFields() {
        var entry = new ContentEntry();
        entry.setId(UUID.randomUUID());
        entry.setVersion(3L);
        entry.setValues(Map.of("cf_title", "Hello"));
        entry.setCreatedAt(OffsetDateTime.now());
        entry.setUpdatedAt(OffsetDateTime.now());
        entry.setCreatedBy(UUID.randomUUID());
        entry.setUpdatedBy(UUID.randomUUID());

        ContentEntryResponse response = mapper.toResponse(entry);

        assertThat(response.getId()).isEqualTo(entry.getId());
        assertThat(response.getVersion()).isEqualTo(3L);
        assertThat(response.getValues()).containsEntry("cf_title", "Hello");
        assertThat(response.getCreatedAt()).isEqualTo(entry.getCreatedAt());
        assertThat(response.getCreatedBy()).isEqualTo(entry.getCreatedBy());
    }

    @Test
    void toListResponse_mapsPageMetadata() {
        var entry = new ContentEntry();
        entry.setId(UUID.randomUUID());
        entry.setVersion(0L);
        entry.setValues(Map.of());
        entry.setCreatedAt(OffsetDateTime.now());
        entry.setUpdatedAt(OffsetDateTime.now());

        var page = new CursorPage<>(List.of(entry), "nextCursorToken", true);

        ContentEntryListResponse response = mapper.toListResponse(page, Map.of());

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getNextCursor()).isEqualTo("nextCursorToken");
        assertThat(response.getHasNext()).isTrue();
    }

    @Test
    void toReferenceResponse_mapsIdAndDisplay() {
        var ref = new EntryReference(UUID.randomUUID(), "Article: Hello");

        EntryReferenceResponse response = mapper.toReferenceResponse(ref);

        assertThat(response.getId()).isEqualTo(ref.id());
        assertThat(response.getDisplay()).isEqualTo("Article: Hello");
    }

    @Test
    void toHistoryResponse_mapsChangedFields() {
        var changes = Map.of(
                "cf_title", new EntryHistoryRecord.FieldChange("Old", "New"));
        var record = new EntryHistoryRecord(
                UUID.randomUUID(), UUID.randomUUID(), "UPDATED",
                changes, OffsetDateTime.now(), UUID.randomUUID());

        EntryHistoryResponse response = mapper.toHistoryResponse(record, Map.of());

        assertThat(response.getId()).isEqualTo(record.id());
        assertThat(response.getAction()).isEqualTo("UPDATED");
        assertThat(response.getChangedFields()).containsKey("cf_title");
        @SuppressWarnings("unchecked")
        Map<String, Object> fieldChange = (Map<String, Object>) response.getChangedFields().get("cf_title");
        assertThat(fieldChange.get("old")).isEqualTo("Old");
        assertThat(fieldChange.get("new")).isEqualTo("New");
    }

    @Test
    void toResponse_withNullValues_returnsEmptyMap() {
        var entry = new ContentEntry();
        entry.setId(UUID.randomUUID());
        entry.setVersion(0L);
        entry.setValues(null);
        entry.setCreatedAt(OffsetDateTime.now());
        entry.setUpdatedAt(OffsetDateTime.now());

        ContentEntryResponse response = mapper.toResponse(entry);

        assertThat(response.getValues()).isEmpty();
    }
}

package io.webcontify.content.management.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntryHistoryRecordTest {

    @Test
    void recordAccessorsReturnCorrectValues() {
        var id = UUID.randomUUID();
        var entryId = UUID.randomUUID();
        var changedBy = UUID.randomUUID();
        var changedAt = OffsetDateTime.now();
        var changes = Map.of(
                "cf_title", new EntryHistoryRecord.FieldChange("old", "new"));

        var record = new EntryHistoryRecord(id, entryId, "CREATED", changes, changedAt, changedBy);

        assertThat(record.id()).isEqualTo(id);
        assertThat(record.entryId()).isEqualTo(entryId);
        assertThat(record.action()).isEqualTo("CREATED");
        assertThat(record.changedFields()).hasSize(1);
        assertThat(record.changedFields().get("cf_title").oldValue()).isEqualTo("old");
        assertThat(record.changedFields().get("cf_title").newValue()).isEqualTo("new");
        assertThat(record.changedAt()).isEqualTo(changedAt);
        assertThat(record.changedBy()).isEqualTo(changedBy);
    }

    @Test
    void fieldChangeRecordAccessorsWork() {
        var change = new EntryHistoryRecord.FieldChange(null, "value");
        assertThat(change.oldValue()).isNull();
        assertThat(change.newValue()).isEqualTo("value");
    }
}

package io.webcontify.content.management.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContentEntryTest {

    @Test
    void noArgConstructorCreatesInstance() {
        var entry = new ContentEntry();
        assertThat(entry).isNotNull();
    }

    @Test
    void gettersAndSettersWorkCorrectly() {
        var entry = new ContentEntry();
        var id = UUID.randomUUID();
        var definitionId = UUID.randomUUID();
        var createdBy = UUID.randomUUID();
        var updatedBy = UUID.randomUUID();
        var now = OffsetDateTime.now();
        var values = Map.<String, Object>of("title", "Hello");

        entry.setId(id);
        entry.setDefinitionId(definitionId);
        entry.setVersion(5L);
        entry.setValues(values);
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        entry.setCreatedBy(createdBy);
        entry.setUpdatedBy(updatedBy);

        assertThat(entry.getId()).isEqualTo(id);
        assertThat(entry.getDefinitionId()).isEqualTo(definitionId);
        assertThat(entry.getVersion()).isEqualTo(5L);
        assertThat(entry.getValues()).isEqualTo(values);
        assertThat(entry.getCreatedAt()).isEqualTo(now);
        assertThat(entry.getUpdatedAt()).isEqualTo(now);
        assertThat(entry.getCreatedBy()).isEqualTo(createdBy);
        assertThat(entry.getUpdatedBy()).isEqualTo(updatedBy);
    }
}

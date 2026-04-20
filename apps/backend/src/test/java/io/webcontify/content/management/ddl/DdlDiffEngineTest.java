package io.webcontify.content.management.ddl;

import static org.assertj.core.api.Assertions.assertThat;

import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DdlDiffEngineTest {

    private final DdlDiffEngine engine = new DdlDiffEngine();

    @Test
    void shouldReturnCreateTableForNewDefinition() {
        var field = createField("title", "ADDED", false, false);

        List<DdlChange> changes = engine.diff(true, List.of(field));

        assertThat(changes).hasSize(1);
        assertThat(changes.getFirst().action()).isEqualTo(DdlAction.CREATE_TABLE);
        assertThat(changes.getFirst().field()).isNull();
    }

    @Test
    void shouldReturnAddColumnForAddedField() {
        var field = createField("title", "ADDED", false, false);

        List<DdlChange> changes = engine.diff(false, List.of(field));

        assertThat(changes).hasSize(1);
        assertThat(changes.getFirst().action()).isEqualTo(DdlAction.ADD_COLUMN);
        assertThat(changes.getFirst().field()).isEqualTo(field);
    }

    @Test
    void shouldReturnAddColumnWithIndexForSortableField() {
        var field = createField("title", "ADDED", true, false);

        List<DdlChange> changes = engine.diff(false, List.of(field));

        assertThat(changes).hasSize(2);
        assertThat(changes.get(0).action()).isEqualTo(DdlAction.ADD_COLUMN);
        assertThat(changes.get(1).action()).isEqualTo(DdlAction.ADD_INDEX);
    }

    @Test
    void shouldReturnAddColumnWithUniqueIndexForUniqueField() {
        var field = createField("email", "ADDED", false, true);

        List<DdlChange> changes = engine.diff(false, List.of(field));

        assertThat(changes).hasSize(2);
        assertThat(changes.get(0).action()).isEqualTo(DdlAction.ADD_COLUMN);
        assertThat(changes.get(1).action()).isEqualTo(DdlAction.ADD_UNIQUE_INDEX);
    }

    @Test
    void shouldReturnDropColumnForRemovedField() {
        var field = createField("obsolete", "REMOVED", false, false);

        List<DdlChange> changes = engine.diff(false, List.of(field));

        assertThat(changes).hasSize(1);
        assertThat(changes.getFirst().action()).isEqualTo(DdlAction.DROP_COLUMN);
    }

    @Test
    void shouldReturnDropAndAddForModifiedField() {
        var field = createField("status", "MODIFIED", true, false);

        List<DdlChange> changes = engine.diff(false, List.of(field));

        assertThat(changes).hasSize(3);
        assertThat(changes.get(0).action()).isEqualTo(DdlAction.DROP_COLUMN);
        assertThat(changes.get(1).action()).isEqualTo(DdlAction.ADD_COLUMN);
        assertThat(changes.get(2).action()).isEqualTo(DdlAction.ADD_INDEX);
    }

    @Test
    void shouldReturnEmptyListWhenNoPendingFields() {
        List<DdlChange> changes = engine.diff(false, List.of());

        assertThat(changes).isEmpty();
    }

    @Test
    void shouldHandleMultiplePendingFields() {
        var added = createField("newField", "ADDED", false, false);
        var removed = createField("oldField", "REMOVED", false, false);

        List<DdlChange> changes = engine.diff(false, List.of(added, removed));

        assertThat(changes).hasSize(2);
        assertThat(changes.get(0).action()).isEqualTo(DdlAction.ADD_COLUMN);
        assertThat(changes.get(1).action()).isEqualTo(DdlAction.DROP_COLUMN);
    }

    private ContentFieldEntity createField(String key, String pendingAction, boolean sortable, boolean unique) {
        var entity = new ContentFieldEntity();
        entity.setId(UUID.randomUUID());
        entity.setFieldKey(key);
        entity.setFieldType("TEXT");
        entity.setPendingAction(pendingAction);
        entity.setSortable(sortable);
        entity.setUniqueValue(unique);
        return entity;
    }
}

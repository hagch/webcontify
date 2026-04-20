package io.webcontify.content.management.storage;

import static org.assertj.core.api.Assertions.assertThat;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.model.EntryHistoryRecord;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContentStorageContractTest {

    @Test
    void interface_declares_save_method() throws NoSuchMethodException {
        Method m = ContentStorage.class.getMethod("save", String.class, ContentDefinition.class, Map.class, UUID.class);
        assertThat(m.getReturnType()).isEqualTo(ContentEntry.class);
    }

    @Test
    void interface_declares_update_method() throws NoSuchMethodException {
        Method m = ContentStorage.class.getMethod("update", String.class, ContentDefinition.class, UUID.class, long.class, Map.class, UUID.class);
        assertThat(m.getReturnType()).isEqualTo(ContentEntry.class);
    }

    @Test
    void interface_declares_softDelete_method() throws NoSuchMethodException {
        Method m = ContentStorage.class.getMethod("softDelete", String.class, ContentDefinition.class, UUID.class, UUID.class);
        assertThat(m.getReturnType()).isEqualTo(void.class);
    }

    @Test
    void interface_declares_findById_method() throws NoSuchMethodException {
        Method m = ContentStorage.class.getMethod("findById", String.class, ContentDefinition.class, UUID.class);
        assertThat(m.getReturnType()).isEqualTo(Optional.class);
    }

    @Test
    void interface_declares_findAll_method() throws NoSuchMethodException {
        Method m = ContentStorage.class.getMethod("findAll", String.class, ContentDefinition.class, int.class);
        assertThat(m.getReturnType()).isEqualTo(List.class);
    }

    @Test
    void interface_declares_findByReferenceField_method() throws NoSuchMethodException {
        Method m = ContentStorage.class.getMethod("findByReferenceField", String.class, ContentDefinition.class, String.class, UUID.class);
        assertThat(m.getReturnType()).isEqualTo(List.class);
    }

    @Test
    void interface_declares_existsByFieldValue_method() throws NoSuchMethodException {
        Method m = ContentStorage.class.getMethod("existsByFieldValue", String.class, ContentDefinition.class, String.class, Object.class, UUID.class);
        assertThat(m.getReturnType()).isEqualTo(boolean.class);
    }

    @Test
    void interface_declares_nullifyField_method() throws NoSuchMethodException {
        Method m = ContentStorage.class.getMethod("nullifyField", String.class, ContentDefinition.class, String.class, UUID.class, UUID.class);
        assertThat(m.getReturnType()).isEqualTo(void.class);
    }

    @Test
    void interface_declares_getHistory_method() throws NoSuchMethodException {
        Method m = ContentStorage.class.getMethod("getHistory", String.class, ContentDefinition.class, UUID.class, int.class);
        assertThat(m.getReturnType()).isEqualTo(List.class);
    }
}

package helpers.suppliers

import io.webcontify.backend.collections.models.apis.WebContifyCollectionFieldApiCreateRequest
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldNumberConfigurationDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldTextConfigurationDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType

class CollectionFieldApiCreateRequestSupplier {

  companion object {
    val NEW_FIELD =
        WebContifyCollectionFieldApiCreateRequest(
            "new_field",
            "New Field",
            WebcontifyCollectionFieldType.NUMBER,
            false,
            WebContifyCollectionFieldNumberConfigurationDto(null, null, false, true, null, null))
    val NEW_PRIMARY_FIELD =
        WebContifyCollectionFieldApiCreateRequest(
            "new_primary_field",
            "New Primary Field",
            WebcontifyCollectionFieldType.NUMBER,
            true,
            WebContifyCollectionFieldNumberConfigurationDto(null, null, false, true, null, null))
    val NUMBER_RELATION_FIELD =
        WebContifyCollectionFieldApiCreateRequest(
            "relation_field",
            "Number Relation Field",
            WebcontifyCollectionFieldType.NUMBER,
            false,
            WebContifyCollectionFieldNumberConfigurationDto(null, null, false, false, null, null))
    val NUMBER_PRIMARY_FIELD =
        WebContifyCollectionFieldApiCreateRequest(
            "number_field",
            "Number Field",
            WebcontifyCollectionFieldType.NUMBER,
            true,
            WebContifyCollectionFieldNumberConfigurationDto(null, null, false, true, null, null))
    val DECIMAL_FIELD =
        WebContifyCollectionFieldApiCreateRequest(
            "decimal_field", "Decimal Field", WebcontifyCollectionFieldType.DECIMAL, false, null)
    val UUID_FIELD =
        WebContifyCollectionFieldApiCreateRequest(
            "uuid_field", "Uuid Field", WebcontifyCollectionFieldType.UUID, false, null)
    val TIMESTAMP_FIELD =
        WebContifyCollectionFieldApiCreateRequest(
            "timestamp_field",
            "Timestamp Field",
            WebcontifyCollectionFieldType.TIMESTAMP,
            false,
            null)
    val BOOLEAN_FIELD =
        WebContifyCollectionFieldApiCreateRequest(
            "boolean_field", "Boolean Field", WebcontifyCollectionFieldType.BOOLEAN, false, null)
    val TEXT_FIELD =
        WebContifyCollectionFieldApiCreateRequest(
            "text_field", "Text Field", WebcontifyCollectionFieldType.TEXT, false, null)

    val FIELD_WITH_INVALID_NAME =
        WebContifyCollectionFieldApiCreateRequest(
            "invalidFieldName", "Invalid Field", WebcontifyCollectionFieldType.NUMBER, true, null)
    val FIELD_WITH_INVALID_NAME_TEXT =
        WebContifyCollectionFieldApiCreateRequest(
            "_?qwd", "Text Field", WebcontifyCollectionFieldType.TEXT, false, null)
    val FIELD_WITH_EMPTY_NAME =
        WebContifyCollectionFieldApiCreateRequest(
            "", "Number Field", WebcontifyCollectionFieldType.NUMBER, true, null)
    val FIELD_WITH_WRONG_CONFIGURATION =
        WebContifyCollectionFieldApiCreateRequest(
            "wrong_configured",
            "Number Field",
            WebcontifyCollectionFieldType.NUMBER,
            true,
            WebContifyCollectionFieldTextConfigurationDto(
                "invalid", null, null, null, null, null, "invalid"))
  }
}

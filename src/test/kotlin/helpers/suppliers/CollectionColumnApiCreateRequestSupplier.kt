package helpers.suppliers

import io.webcontify.backend.collections.models.apis.WebContifyCollectionColumnApiCreateRequest
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnNumberConfigurationDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnTextConfigurationDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType

class CollectionColumnApiCreateRequestSupplier {

  companion object {
    val NEW_COLUMN =
        WebContifyCollectionColumnApiCreateRequest(
            "new_column",
            "New Column",
            WebcontifyCollectionColumnType.NUMBER,
            false,
            WebContifyCollectionColumnNumberConfigurationDto(null, null, false, true, null, null))
    val NEW_PRIMARY_COLUMN =
        WebContifyCollectionColumnApiCreateRequest(
            "new_column",
            "New Column",
            WebcontifyCollectionColumnType.NUMBER,
            true,
            WebContifyCollectionColumnNumberConfigurationDto(null, null, false, true, null, null))
    val NUMBER_PRIMARY_COLUMN =
        WebContifyCollectionColumnApiCreateRequest(
            "number_column",
            "Number Column",
            WebcontifyCollectionColumnType.NUMBER,
            true,
            WebContifyCollectionColumnNumberConfigurationDto(null, null, false, true, null, null))
    val DECIMAL_COLUMN =
        WebContifyCollectionColumnApiCreateRequest(
            "decimal_column", "Number Column", WebcontifyCollectionColumnType.DECIMAL, false, null)
    val UUID_COLUMN =
        WebContifyCollectionColumnApiCreateRequest(
            "uuid_column", "Number Column", WebcontifyCollectionColumnType.UUID, false, null)
    val TIMESTAMP_COLUMN =
        WebContifyCollectionColumnApiCreateRequest(
            "timestamp_column",
            "Number Column",
            WebcontifyCollectionColumnType.TIMESTAMP,
            false,
            null)
    val BOOLEAN_COLUMN =
        WebContifyCollectionColumnApiCreateRequest(
            "boolean_column", "Number Column", WebcontifyCollectionColumnType.BOOLEAN, false, null)
    val TEXT_COLUMN =
        WebContifyCollectionColumnApiCreateRequest(
            "text_column", "Number Column", WebcontifyCollectionColumnType.TEXT, false, null)

    val COLUMN_WITH_INVALID_NAME =
        WebContifyCollectionColumnApiCreateRequest(
            "invalidColumnName", "Number Column", WebcontifyCollectionColumnType.NUMBER, true, null)
    val COLUMN_WITH_INVALID_NAME_TEXT =
        WebContifyCollectionColumnApiCreateRequest(
            "_?qwd", "Text Column", WebcontifyCollectionColumnType.TEXT, false, null)
    val COLUMN_WITH_EMPTY_NAME =
        WebContifyCollectionColumnApiCreateRequest(
            "", "Number Column", WebcontifyCollectionColumnType.NUMBER, true, null)
    val COLUMN_WITH_WRONG_CONFIGURATION =
        WebContifyCollectionColumnApiCreateRequest(
            "wrong_configured",
            "Number Column",
            WebcontifyCollectionColumnType.NUMBER,
            true,
            WebContifyCollectionColumnTextConfigurationDto(
                "invalid", null, null, null, null, null, "invalid"))
  }
}

package helpers.suppliers

import io.webcontify.backend.collections.models.apis.WebContifyCollectionColumnApiUpdateRequest
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType

class CollectionColumnApiUpdateRequestSupplier {

  companion object {
    val NUMBER_PRIMARY_COLUMN_NAME_CHANGED =
        Pair(
            "number_column",
            WebContifyCollectionColumnApiUpdateRequest(
                "number_column_changed",
                "Number Column",
                WebcontifyCollectionColumnType.NUMBER,
                true))
    val NUMBER_COLUMN_PRIMARY_CHANGED =
        Pair(
            "number_column",
            WebContifyCollectionColumnApiUpdateRequest(
                "number_column", "Number Column", WebcontifyCollectionColumnType.NUMBER, false))
    val DECIMAL_COLUMN_TYPE_CHANGED =
        Pair(
            "decimal_column",
            WebContifyCollectionColumnApiUpdateRequest(
                "decimal_column", "Decimal Column", WebcontifyCollectionColumnType.NUMBER, false))
    val DECIMAL_COLUMN_NAME_CHANGED =
        Pair(
            "decimal_column",
            WebContifyCollectionColumnApiUpdateRequest(
                "decimal_column_changed",
                "Decimal Column",
                WebcontifyCollectionColumnType.DECIMAL,
                false))
    val EXISTING_COLUMN_NAME =
        Pair(
            DECIMAL_COLUMN_NAME_CHANGED.first,
            WebContifyCollectionColumnApiUpdateRequest(
                CollectionColumnApiCreateRequestSupplier.NUMBER_PRIMARY_COLUMN.name,
                CollectionColumnApiCreateRequestSupplier.NUMBER_PRIMARY_COLUMN.displayName,
                WebcontifyCollectionColumnType.DECIMAL,
                false))
    val RELATED_COLUMN_CHANGED =
        Pair(
            "related_column",
            WebContifyCollectionColumnApiUpdateRequest(
                "related_column_changed",
                "Related Column",
                WebcontifyCollectionColumnType.NUMBER,
                false))
  }
}

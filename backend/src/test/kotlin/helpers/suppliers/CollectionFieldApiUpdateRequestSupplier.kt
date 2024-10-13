package helpers.suppliers

import io.webcontify.backend.collections.models.apis.WebContifyCollectionFieldApiUpdateRequest
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType

class CollectionFieldApiUpdateRequestSupplier {

  companion object {
    val NUMBER_PRIMARY_FIELD_NAME_CHANGED =
        Pair(
            "numberField",
            WebContifyCollectionFieldApiUpdateRequest(
                "numberFieldChanged", "Number Field", WebcontifyCollectionFieldType.NUMBER, true))
    val NUMBER_FIELD_PRIMARY_CHANGED =
        Pair(
            "numberField",
            WebContifyCollectionFieldApiUpdateRequest(
                "numberField", "Number Field", WebcontifyCollectionFieldType.NUMBER, false))
    val DECIMAL_FIELD_TYPE_CHANGED =
        Pair(
            "decimalField",
            WebContifyCollectionFieldApiUpdateRequest(
                "decimalField", "Decimal Field", WebcontifyCollectionFieldType.NUMBER, false))
    val DECIMAL_FIELD_NAME_CHANGED =
        Pair(
            "decimalField",
            WebContifyCollectionFieldApiUpdateRequest(
                "decimalFieldChanged",
                "Decimal Field",
                WebcontifyCollectionFieldType.DECIMAL,
                false))
    val EXISTING_FIELD_NAME =
        Pair(
            DECIMAL_FIELD_NAME_CHANGED.first,
            WebContifyCollectionFieldApiUpdateRequest(
                CollectionFieldApiCreateRequestSupplier.NUMBER_PRIMARY_FIELD.name,
                CollectionFieldApiCreateRequestSupplier.NUMBER_PRIMARY_FIELD.displayName,
                WebcontifyCollectionFieldType.DECIMAL,
                false))
    val RELATED_FIELD_CHANGED =
        Pair(
            "relatedField",
            WebContifyCollectionFieldApiUpdateRequest(
                "relatedFieldChanged",
                "Related Field",
                WebcontifyCollectionFieldType.NUMBER,
                false))
  }
}

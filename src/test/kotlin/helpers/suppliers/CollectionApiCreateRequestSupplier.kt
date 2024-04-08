package helpers.suppliers

import helpers.suppliers.CollectionFieldApiCreateRequestSupplier.Companion.BOOLEAN_FIELD
import helpers.suppliers.CollectionFieldApiCreateRequestSupplier.Companion.DECIMAL_FIELD
import helpers.suppliers.CollectionFieldApiCreateRequestSupplier.Companion.FIELD_WITH_EMPTY_NAME
import helpers.suppliers.CollectionFieldApiCreateRequestSupplier.Companion.FIELD_WITH_INVALID_NAME
import helpers.suppliers.CollectionFieldApiCreateRequestSupplier.Companion.FIELD_WITH_INVALID_NAME_TEXT
import helpers.suppliers.CollectionFieldApiCreateRequestSupplier.Companion.FIELD_WITH_WRONG_CONFIGURATION
import helpers.suppliers.CollectionFieldApiCreateRequestSupplier.Companion.NUMBER_PRIMARY_FIELD
import helpers.suppliers.CollectionFieldApiCreateRequestSupplier.Companion.NUMBER_RELATION_FIELD
import helpers.suppliers.CollectionFieldApiCreateRequestSupplier.Companion.TEXT_FIELD
import helpers.suppliers.CollectionFieldApiCreateRequestSupplier.Companion.TIMESTAMP_FIELD
import helpers.suppliers.CollectionFieldApiCreateRequestSupplier.Companion.UUID_FIELD
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiCreateRequest

class CollectionApiCreateRequestSupplier {

  companion object {
    private var collectionId: Long = 0

    val COLLECTION_WITHOUT_FIELDS =
        WebContifyCollectionApiCreateRequest("without_fields", "DisplayName", listOf())
    val COLLECTION_WITH_EMPTY_NAME =
        WebContifyCollectionApiCreateRequest("", "DisplayName", listOf(NUMBER_PRIMARY_FIELD))
    val COLLECTION_WITH_INVALID_NAME =
        WebContifyCollectionApiCreateRequest("_testA?", "DisplayName", listOf(NUMBER_PRIMARY_FIELD))
    val COLLECTION_WITHOUT_PRIMARY_FIELD =
        WebContifyCollectionApiCreateRequest(
            "without_primary_key", "DisplayName", listOf(DECIMAL_FIELD))
    val COLLECTION_WITH_INVALID_FIELD_NAMES =
        WebContifyCollectionApiCreateRequest(
            "invalid_field_name",
            "DisplayName",
            listOf(FIELD_WITH_INVALID_NAME, FIELD_WITH_INVALID_NAME_TEXT, FIELD_WITH_EMPTY_NAME))
    val COLLECTION_WITH_FIELD_WRONG_CONFIGURATION =
        WebContifyCollectionApiCreateRequest(
            "empty_field_name", "DisplayName", listOf(FIELD_WITH_WRONG_CONFIGURATION))

    fun getCollectionWithValidNameOnePrimaryField(): WebContifyCollectionApiCreateRequest {
      return WebContifyCollectionApiCreateRequest(
          "collection_" + collectionId++.toString(),
          "DisplayName",
          listOf(
              NUMBER_PRIMARY_FIELD,
              DECIMAL_FIELD,
              UUID_FIELD,
              TIMESTAMP_FIELD,
              BOOLEAN_FIELD,
              TEXT_FIELD))
    }

    fun getCollectionRelationField(): WebContifyCollectionApiCreateRequest {
      return WebContifyCollectionApiCreateRequest(
          "collection_" + collectionId++.toString(),
          "DisplayName",
          listOf(NUMBER_PRIMARY_FIELD, NUMBER_RELATION_FIELD))
    }

    fun getCollectionWithValidNameMultiplePrimaryFields(): WebContifyCollectionApiCreateRequest {
      return WebContifyCollectionApiCreateRequest(
          "collection_" + collectionId++.toString(),
          "DisplayName",
          listOf(
              NUMBER_PRIMARY_FIELD,
              DECIMAL_FIELD,
              UUID_FIELD.copy(name = "uuid_primary_field", isPrimaryKey = true),
              TIMESTAMP_FIELD,
              BOOLEAN_FIELD,
              TEXT_FIELD))
    }
  }
}

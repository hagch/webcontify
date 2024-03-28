package helpers.suppliers

import helpers.suppliers.CollectionColumnApiCreateRequestSupplier.Companion.BOOLEAN_COLUMN
import helpers.suppliers.CollectionColumnApiCreateRequestSupplier.Companion.COLUMN_WITH_EMPTY_NAME
import helpers.suppliers.CollectionColumnApiCreateRequestSupplier.Companion.COLUMN_WITH_INVALID_NAME
import helpers.suppliers.CollectionColumnApiCreateRequestSupplier.Companion.COLUMN_WITH_INVALID_NAME_TEXT
import helpers.suppliers.CollectionColumnApiCreateRequestSupplier.Companion.COLUMN_WITH_WRONG_CONFIGURATION
import helpers.suppliers.CollectionColumnApiCreateRequestSupplier.Companion.DECIMAL_COLUMN
import helpers.suppliers.CollectionColumnApiCreateRequestSupplier.Companion.NUMBER_PRIMARY_COLUMN
import helpers.suppliers.CollectionColumnApiCreateRequestSupplier.Companion.TEXT_COLUMN
import helpers.suppliers.CollectionColumnApiCreateRequestSupplier.Companion.TIMESTAMP_COLUMN
import helpers.suppliers.CollectionColumnApiCreateRequestSupplier.Companion.UUID_COLUMN
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiCreateRequest
import io.webcontify.backend.collections.models.dtos.*

class CollectionApiCreateRequestSupplier {

  companion object {
    private var collectionId: Long = 0

    val COLLECTION_WITHOUT_COLUMNS =
        WebContifyCollectionApiCreateRequest("without_columns", "DisplayName", listOf(), null)
    val COLLECTION_WITH_EMPTY_NAME =
        WebContifyCollectionApiCreateRequest("", "DisplayName", listOf(NUMBER_PRIMARY_COLUMN), null)
    val COLLECTION_WITH_INVALID_NAME =
        WebContifyCollectionApiCreateRequest(
            "_testA?", "DisplayName", listOf(NUMBER_PRIMARY_COLUMN), null)
    val COLLECTION_WITHOUT_PRIMARY_COLUMN =
        WebContifyCollectionApiCreateRequest(
            "without_primary_key", "DisplayName", listOf(DECIMAL_COLUMN), null)
    val COLLECTION_WITH_INVALID_COLUMN_NAMES =
        WebContifyCollectionApiCreateRequest(
            "invalid_column_name",
            "DisplayName",
            listOf(COLUMN_WITH_INVALID_NAME, COLUMN_WITH_INVALID_NAME_TEXT, COLUMN_WITH_EMPTY_NAME),
            null)
    val COLLECTION_WITH_COLUMN_WRONG_CONFIGURATION =
        WebContifyCollectionApiCreateRequest(
            "empty_column_name", "DisplayName", listOf(COLUMN_WITH_WRONG_CONFIGURATION), null)

    fun getCollectionWithValidNameOnePrimaryColumn(): WebContifyCollectionApiCreateRequest {
      return WebContifyCollectionApiCreateRequest(
          collectionId++.toString(),
          "DisplayName",
          listOf(
              NUMBER_PRIMARY_COLUMN,
              DECIMAL_COLUMN,
              UUID_COLUMN,
              TIMESTAMP_COLUMN,
              BOOLEAN_COLUMN,
              TEXT_COLUMN),
          null)
    }

    fun getCollectionWithValidNameMultiplePrimaryColumns(): WebContifyCollectionApiCreateRequest {
      return WebContifyCollectionApiCreateRequest(
          collectionId++.toString(),
          "DisplayName",
          listOf(
              NUMBER_PRIMARY_COLUMN,
              DECIMAL_COLUMN,
              UUID_COLUMN.copy(name = "uuid_primary_column", isPrimaryKey = true),
              TIMESTAMP_COLUMN,
              BOOLEAN_COLUMN,
              TEXT_COLUMN),
          null)
    }
  }
}

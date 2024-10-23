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
import io.webcontify.backend.collections.models.apis.WebContifyCollectionFieldApiCreateRequest
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldNumberConfigurationDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType

class CollectionApiCreateRequestSupplier {

  companion object {
    private var collectionId: Long = 0

    val COLLECTION_WITHOUT_FIELDS =
        WebContifyCollectionApiCreateRequest("withoutFields", "DisplayName", listOf())
    val COLLECTION_WITH_EMPTY_NAME =
        WebContifyCollectionApiCreateRequest("", "DisplayName", listOf(NUMBER_PRIMARY_FIELD))
    val COLLECTION_WITH_INVALID_NAME =
        WebContifyCollectionApiCreateRequest("_testA?", "DisplayName", listOf(NUMBER_PRIMARY_FIELD))
    val COLLECTION_WITHOUT_PRIMARY_FIELD =
        WebContifyCollectionApiCreateRequest(
            "withoutPrimaryKey", "DisplayName", listOf(DECIMAL_FIELD))
    val COLLECTION_WITH_INVALID_FIELD_NAMES =
        WebContifyCollectionApiCreateRequest(
            "invalidFieldName",
            "DisplayName",
            listOf(FIELD_WITH_INVALID_NAME, FIELD_WITH_INVALID_NAME_TEXT, FIELD_WITH_EMPTY_NAME))
    val COLLECTION_WITH_FIELD_WRONG_CONFIGURATION =
        WebContifyCollectionApiCreateRequest(
            "emptyFieldName", "DisplayName", listOf(FIELD_WITH_WRONG_CONFIGURATION))

    fun getCollectionWithValidNameOnePrimaryField(): WebContifyCollectionApiCreateRequest {
      return WebContifyCollectionApiCreateRequest(
          "collection" + collectionId++.toString(),
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
          "collection" + collectionId++.toString(),
          "DisplayName",
          listOf(NUMBER_PRIMARY_FIELD, NUMBER_RELATION_FIELD))
    }

    fun getCollectionWithValidNameMultiplePrimaryFields(): WebContifyCollectionApiCreateRequest {
      return WebContifyCollectionApiCreateRequest(
          "collection" + collectionId++.toString(),
          "DisplayName",
          listOf(
              NUMBER_PRIMARY_FIELD,
              DECIMAL_FIELD,
              UUID_FIELD.copy(name = "uuidPrimaryField", isPrimaryKey = true),
              TIMESTAMP_FIELD,
              BOOLEAN_FIELD,
              TEXT_FIELD))
    }

    fun getUserCollection(): WebContifyCollectionApiCreateRequest {
      return WebContifyCollectionApiCreateRequest(
          "collection" + collectionId++.toString(),
          "User",
          listOf(
              NUMBER_PRIMARY_FIELD,
              WebContifyCollectionFieldApiCreateRequest(
                  "userName", "User Name", WebcontifyCollectionFieldType.TEXT, false, null),
              WebContifyCollectionFieldApiCreateRequest(
                  "organizationId",
                  "Organization Id",
                  WebcontifyCollectionFieldType.NUMBER,
                  false,
                  WebContifyCollectionFieldNumberConfigurationDto(
                      null, null, true, true, null, null))))
    }

    fun getOrganizationCollection(): WebContifyCollectionApiCreateRequest {
      return WebContifyCollectionApiCreateRequest(
          "collection" + collectionId++.toString(),
          "Organization",
          listOf(
              NUMBER_PRIMARY_FIELD,
              WebContifyCollectionFieldApiCreateRequest(
                  "organizationName",
                  "Organization Name",
                  WebcontifyCollectionFieldType.TEXT,
                  false,
                  null),
          ))
    }

    fun getUserChildrenCollection(): WebContifyCollectionApiCreateRequest {
      return WebContifyCollectionApiCreateRequest(
          "collection" + collectionId++.toString(),
          "User Children",
          listOf(
              NUMBER_PRIMARY_FIELD,
              WebContifyCollectionFieldApiCreateRequest(
                  "userId",
                  "User Id",
                  WebcontifyCollectionFieldType.NUMBER,
                  false,
                  WebContifyCollectionFieldNumberConfigurationDto(
                      null, null, false, false, null, null)),
              WebContifyCollectionFieldApiCreateRequest(
                  "childName", "Child Name", WebcontifyCollectionFieldType.TEXT, false, null),
          ))
    }

    fun getNetworkProviderCollection(): WebContifyCollectionApiCreateRequest {
      return WebContifyCollectionApiCreateRequest(
          "collection" + collectionId++.toString(),
          "Network Provider",
          listOf(
              NUMBER_PRIMARY_FIELD,
              WebContifyCollectionFieldApiCreateRequest(
                  "networkProviderName",
                  "Network provider Name",
                  WebcontifyCollectionFieldType.TEXT,
                  false,
                  null),
          ))
    }
  }
}

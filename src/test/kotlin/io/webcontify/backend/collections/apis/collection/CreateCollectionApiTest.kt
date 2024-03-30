package io.webcontify.backend.collections.apis.collection

import helpers.asserts.equalsTo
import helpers.asserts.errorSizeEquals
import helpers.asserts.instanceEquals
import helpers.asserts.timestampNotNull
import helpers.setups.api.ApiTestSetup
import helpers.suppliers.CollectionApiCreateRequestSupplier
import helpers.suppliers.CollectionColumnApiCreateRequestSupplier.Companion.NUMBER_PRIMARY_COLUMN
import helpers.suppliers.CollectionRelationApiCreateRequestSupplier
import io.restassured.module.mockmvc.kotlin.extensions.Extract
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiCreateRequest
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationFieldDto
import io.webcontify.backend.collections.models.errors.Error
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.models.errors.ErrorResponse
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

// TODO max size of column and table names cause constraint names cannot be endless long,
// transaction testing
class CreateCollectionApiTest : ApiTestSetup() {

  @Test
  fun `(CreateCollection) endpoint should throw error column required on collection creation without columns`() {
    val errorResponse =
        sendInvalidCollectionCreation(CollectionApiCreateRequestSupplier.COLLECTION_WITHOUT_COLUMNS)

    generalErrorChecks(errorResponse)
    errorResponse.errorSizeEquals(1)
    errorResponse.errors[0].equalsTo(ErrorCode.COLUMN_REQUIRED, ErrorCode.COLUMN_REQUIRED.message)
  }

  @Test
  fun `(CreateCollection) endpoint should throw error invalid name on collection creation with invalid name`() {
    val errorResponse =
        sendInvalidCollectionCreation(
            CollectionApiCreateRequestSupplier.COLLECTION_WITH_INVALID_NAME)

    generalErrorChecks(errorResponse)
    errorResponse.errorSizeEquals(1)
    errorResponse.errors[0].equalsTo(ErrorCode.INVALID_NAME, ErrorCode.INVALID_NAME.message)
  }

  @Test
  fun `(CreateCollection) endpoint should throw error no name provided on collection creation with empty string as name`() {
    val errorResponse =
        sendInvalidCollectionCreation(CollectionApiCreateRequestSupplier.COLLECTION_WITH_EMPTY_NAME)

    generalErrorChecks(errorResponse)
    errorResponse.errorSizeEquals(1)
    errorResponse.errors[0].equalsTo(ErrorCode.NAME_REQUIRED, ErrorCode.NAME_REQUIRED.message)
  }

  @Test
  fun `(CreateCollection) endpoint should throw error primary column required on collection creation with no column which has primary key true`() {
    val errorResponse =
        sendInvalidCollectionCreation(
            CollectionApiCreateRequestSupplier.COLLECTION_WITHOUT_PRIMARY_COLUMN)

    generalErrorChecks(errorResponse)
    errorResponse.errorSizeEquals(1)
    errorResponse.errors[0].equalsTo(
        ErrorCode.UNABLE_TO_CREATE_COLLECTION, ErrorCode.UNABLE_TO_CREATE_COLLECTION.message)
  }

  @Test
  fun `(CreateCollection) endpoint should throw multiple errors on collection creation with multiple columns which have invalid names`() {
    val errorResponse =
        sendInvalidCollectionCreation(
            CollectionApiCreateRequestSupplier.COLLECTION_WITH_INVALID_COLUMN_NAMES)

    generalErrorChecks(errorResponse)
    errorResponse.errorSizeEquals(3)
    assertTrue(
        errorResponse.errors.containsAll(
            listOf(Error(ErrorCode.INVALID_NAME), Error(ErrorCode.NAME_REQUIRED))))
    assertEquals(2, errorResponse.errors.filter { it.code == ErrorCode.INVALID_NAME }.size)
  }

  @Test
  fun `(CreateCollection) endpoint should throw error if wrong configuration for column is passed`() {
    val errorResponse =
        sendInvalidCollectionCreation(
            CollectionApiCreateRequestSupplier.COLLECTION_WITH_COLUMN_WRONG_CONFIGURATION)

    generalErrorChecks(errorResponse)
    errorResponse.errorSizeEquals(1)
    assertEquals(errorResponse.errors[0].code, ErrorCode.INVALID_REQUEST_BODY)
  }

  @Test
  fun `(CreateCollection) endpoint should create collection with all column types`() {
    val collection = CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryColumn()

    sendValidCollectionCreation(collection)
  }

  @Test
  fun `(CreateCollection) endpoint should create collection with multiple primary key columns`() {
    val collection =
        CollectionApiCreateRequestSupplier.getCollectionWithValidNameMultiplePrimaryColumns()

    sendValidCollectionCreation(collection)
  }

  @Test
  fun `(CreateCollection) endpoint should throw already exists on collection with name already exists`() {
    val collection = CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryColumn()
    sendValidCollectionCreation(collection)

    val errorResponse = sendInvalidCollectionCreation(collection, HttpStatus.CONFLICT)

    generalErrorChecks(errorResponse)
    errorResponse.errorSizeEquals(1)
    errorResponse.errors[0].equalsTo(
        ErrorCode.COLLECTION_WITH_NAME_ALREADY_EXISTS,
        String.format(ErrorCode.COLLECTION_WITH_NAME_ALREADY_EXISTS.message, collection.name))
  }

  @Test
  fun `(CreateCollection) endpoint should create one to one relation`() {
    val collectionForRelation =
        CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryColumn()
    val relatedCollectionId = getCollectionIdOfCreation(collectionForRelation)

    val collectionWithRelation =
        CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryColumn()
            .copy(
                relations =
                    setOf(
                        CollectionRelationApiCreateRequestSupplier.getOneToOneRelation(
                            setOf(
                                WebContifyCollectionRelationFieldDto(
                                    NUMBER_PRIMARY_COLUMN.name, NUMBER_PRIMARY_COLUMN.name)),
                            relatedCollectionId)))
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(collectionWithRelation)
    } When
        {
          post(COLLECTIONS_PATH)
        } Then
        {
          status(HttpStatus.CREATED)
          body("relations", hasSize<MutableCollection<Any>>(equalTo(1)))
          body("relations[0].referencedCollectionId", equalTo(relatedCollectionId))
          body("relations[0].fields", hasSize<MutableCollection<Any>>(1))
        }
  }

  @Test
  fun `(CreateCollection) endpoint should create one to many relation`() {
    val collectionForRelation =
        CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryColumn()
    val relatedCollectionId = getCollectionIdOfCreation(collectionForRelation)

    val collectionWithRelation =
        CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryColumn()
            .copy(
                relations =
                    setOf(
                        CollectionRelationApiCreateRequestSupplier.getOneToManyRelation(
                            setOf(
                                WebContifyCollectionRelationFieldDto(
                                    NUMBER_PRIMARY_COLUMN.name, NUMBER_PRIMARY_COLUMN.name)),
                            relatedCollectionId)))
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(collectionWithRelation)
    } When
        {
          post(COLLECTIONS_PATH)
        } Then
        {
          status(HttpStatus.CREATED)
          body("relations", hasSize<MutableCollection<Any>>(equalTo(1)))
          body("relations[0].referencedCollectionId", equalTo(relatedCollectionId))
          body("relations[0].fields", hasSize<MutableCollection<Any>>(1))
        }
  }

  @Test
  fun `(CreateCollection) endpoint should create many to one relation`() {
    val collectionForRelation =
        CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryColumn()
    val relatedCollectionId = getCollectionIdOfCreation(collectionForRelation)

    val collectionWithRelation =
        CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryColumn()
            .copy(
                relations =
                    setOf(
                        CollectionRelationApiCreateRequestSupplier.getManyToOneRelation(
                            setOf(
                                WebContifyCollectionRelationFieldDto(
                                    NUMBER_PRIMARY_COLUMN.name, NUMBER_PRIMARY_COLUMN.name)),
                            relatedCollectionId)))
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(collectionWithRelation)
    } When
        {
          post(COLLECTIONS_PATH)
        } Then
        {
          status(HttpStatus.CREATED)
          body("relations", hasSize<MutableCollection<Any>>(equalTo(1)))
          body("relations[0].referencedCollectionId", equalTo(relatedCollectionId))
          body("relations[0].fields", hasSize<MutableCollection<Any>>(1))
        }
  }

  @Test
  fun `(CreateCollection) endpoint should create many to many relation`() {
    val collectionForRelation =
        CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryColumn()
    val relatedCollectionId = getCollectionIdOfCreation(collectionForRelation)

    val collectionWithRelation =
        CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryColumn()
            .copy(
                relations =
                    setOf(
                        CollectionRelationApiCreateRequestSupplier.getManyToManyRelation(
                            setOf(
                                WebContifyCollectionRelationFieldDto(
                                    NUMBER_PRIMARY_COLUMN.name, NUMBER_PRIMARY_COLUMN.name)),
                            relatedCollectionId)))
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(collectionWithRelation)
    } When
        {
          post(COLLECTIONS_PATH)
        } Then
        {
          status(HttpStatus.CREATED)
          body("relations", hasSize<MutableCollection<Any>>(equalTo(1)))
          body("relations[0].referencedCollectionId", `is`(not(equalTo(relatedCollectionId))))
          body("relations[0].fields", hasSize<MutableCollection<Any>>(1))
        }
  }

  private fun sendInvalidCollectionCreation(
      collection: WebContifyCollectionApiCreateRequest,
      status: HttpStatus = HttpStatus.BAD_REQUEST
  ): ErrorResponse {
    return Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(collection)
    } When
        {
          post(COLLECTIONS_PATH)
        } Then
        {
          status(status)
        } Extract
        {
          body().`as`(ErrorResponse::class.java)
        }
  }

  private fun sendValidCollectionCreation(collection: WebContifyCollectionApiCreateRequest) {
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(collection)
    } When
        {
          post(COLLECTIONS_PATH)
        } Then
        {
          status(HttpStatus.CREATED)
          body("id", notNullValue())
          body(
              "columns",
              hasSize<MutableCollection<Map<String, Any>>>(equalTo(collection.columns.size)))
          body("name", equalTo(collection.name))
          body("displayName", equalTo(collection.displayName))
        }
  }

  private fun getCollectionIdOfCreation(collection: WebContifyCollectionApiCreateRequest): Int {
    return Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(collection)
    } When
        {
          post(COLLECTIONS_PATH)
        } Then
        {
          status(HttpStatus.CREATED)
          body("id", notNullValue())
          body(
              "columns",
              hasSize<MutableCollection<Map<String, Any>>>(equalTo(collection.columns.size)))
          body("name", equalTo(collection.name))
          body("displayName", equalTo(collection.displayName))
        } Extract
        {
          body().jsonPath().getInt("id")
        }
  }

  private fun generalErrorChecks(error: ErrorResponse) {
    error.instanceEquals("/api/v1/collections")
    error.timestampNotNull()
  }
}

package io.webcontify.backend.collections.apis.collection

import helpers.asserts.equalsTo
import helpers.asserts.errorSizeEquals
import helpers.asserts.instanceEquals
import helpers.asserts.timestampNotNull
import helpers.setups.api.ApiIntegrationTestSetup
import helpers.suppliers.CollectionApiCreateRequestSupplier
import io.restassured.module.mockmvc.kotlin.extensions.Extract
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiCreateRequest
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

// TODO max size of field and table names cause constraint names cannot be endless long,
class CreateCollectionApiIntegrationTest : ApiIntegrationTestSetup() {

  @Test
  fun `(CreateCollection) endpoint should throw error field required on collection creation without fields`() {
    val errorResponse =
        sendInvalidCollectionCreation(CollectionApiCreateRequestSupplier.COLLECTION_WITHOUT_FIELDS)

    generalErrorChecks(errorResponse)
    errorResponse.errorSizeEquals(1)
    errorResponse.errors[0].equalsTo(ErrorCode.FIELD_REQUIRED, ErrorCode.FIELD_REQUIRED.message)
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
    errorResponse.errorSizeEquals(2)
    errorResponse.errors
        .first { it.code == ErrorCode.INVALID_NAME }
        .equalsTo(ErrorCode.INVALID_NAME, ErrorCode.INVALID_NAME.message)
    errorResponse.errors
        .first { it.code == ErrorCode.NAME_REQUIRED }
        .equalsTo(ErrorCode.NAME_REQUIRED, ErrorCode.NAME_REQUIRED.message)
  }

  @Test
  fun `(CreateCollection) endpoint should throw error primary field required on collection creation with no field which has primary key true`() {
    val errorResponse =
        sendInvalidCollectionCreation(
            CollectionApiCreateRequestSupplier.COLLECTION_WITHOUT_PRIMARY_FIELD)

    generalErrorChecks(errorResponse)
    errorResponse.errorSizeEquals(1)
    errorResponse.errors[0].equalsTo(
        ErrorCode.UNABLE_TO_CREATE_COLLECTION, ErrorCode.UNABLE_TO_CREATE_COLLECTION.message)
  }

  @Test
  fun `(CreateCollection) endpoint should throw multiple errors on collection creation with multiple fields which have invalid names`() {
    val errorResponse =
        sendInvalidCollectionCreation(
            CollectionApiCreateRequestSupplier.COLLECTION_WITH_INVALID_FIELD_NAMES)

    generalErrorChecks(errorResponse)
    errorResponse.errorSizeEquals(4)
    assertTrue(
        errorResponse.errors.containsAll(
            listOf(Error(ErrorCode.INVALID_NAME), Error(ErrorCode.NAME_REQUIRED))))
    assertEquals(3, errorResponse.errors.filter { it.code == ErrorCode.INVALID_NAME }.size)
  }

  @Test
  fun `(CreateCollection) endpoint should throw error if wrong configuration for field is passed`() {
    val errorResponse =
        sendInvalidCollectionCreation(
            CollectionApiCreateRequestSupplier.COLLECTION_WITH_FIELD_WRONG_CONFIGURATION)

    generalErrorChecks(errorResponse)
    errorResponse.errorSizeEquals(1)
    assertEquals(errorResponse.errors[0].code, ErrorCode.INVALID_REQUEST_BODY)
  }

  @Test
  fun `(CreateCollection) endpoint should create collection with all field types`() {
    val collection = CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryField()

    sendValidCollectionCreation(collection)
  }

  @Test
  fun `(CreateCollection) endpoint should create collection with multiple primary key fields`() {
    val collection =
        CollectionApiCreateRequestSupplier.getCollectionWithValidNameMultiplePrimaryFields()

    sendValidCollectionCreation(collection)
  }

  @Test
  fun `(CreateCollection) endpoint should throw already exists on collection with name already exists`() {
    val collection = CollectionApiCreateRequestSupplier.getCollectionWithValidNameOnePrimaryField()
    sendValidCollectionCreation(collection)

    val errorResponse = sendInvalidCollectionCreation(collection, HttpStatus.CONFLICT)

    generalErrorChecks(errorResponse)
    errorResponse.errorSizeEquals(1)
    errorResponse.errors[0].equalsTo(
        ErrorCode.COLLECTION_WITH_NAME_ALREADY_EXISTS,
        String.format(ErrorCode.COLLECTION_WITH_NAME_ALREADY_EXISTS.message, collection.name))
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
              "fields",
              hasSize<MutableCollection<Map<String, Any>>>(equalTo(collection.fields.size)))
          body("name", equalTo(collection.name))
          body("displayName", equalTo(collection.displayName))
        }
  }

  private fun generalErrorChecks(error: ErrorResponse) {
    error.instanceEquals("/api/v1/collections")
    error.timestampNotNull()
  }
}

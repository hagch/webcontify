package io.webcontify.backend.collections.apis.collection

import helpers.asserts.equalsTo
import helpers.asserts.errorSizeEquals
import helpers.asserts.instanceEquals
import helpers.asserts.timestampNotNull
import helpers.setups.api.ApiIntegrationTestSetup
import io.restassured.module.mockmvc.kotlin.extensions.Extract
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiUpdateRequest
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.models.errors.ErrorResponse
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql

class UpdateCollectionApiIntegrationTest : ApiIntegrationTestSetup() {

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(UpdateCollectionById) should update collection name and display name`() {
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(WebContifyCollectionApiUpdateRequest("test", "Test"))
    } When
        {
          put("$COLLECTIONS_PATH/1")
        } Then
        {
          status(HttpStatus.OK)
          body("id", equalTo(1))
          body("name", equalTo("test"))
          body("displayName", equalTo("Test"))
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(UpdateCollectionById) should return error on name is invalid`() {
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(WebContifyCollectionApiUpdateRequest("invalid_Name", "Test"))
        } When
            {
              put("$COLLECTIONS_PATH/1")
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }

    errorResponse.instanceEquals("/$COLLECTIONS_PATH/1")
    errorResponse.timestampNotNull()
    errorResponse.errorSizeEquals(1)
    errorResponse.errors[0].equalsTo(ErrorCode.INVALID_NAME, ErrorCode.INVALID_NAME.message)
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(UpdateCollectionById) should return error on name already exists`() {
    val newCollectionName = "allFieldTypesPrimaryUuid"
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(WebContifyCollectionApiUpdateRequest(newCollectionName, "Test"))
        } When
            {
              put("$COLLECTIONS_PATH/1")
            } Then
            {
              status(HttpStatus.CONFLICT)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }

    errorResponse.instanceEquals("/$COLLECTIONS_PATH/1")
    errorResponse.timestampNotNull()
    errorResponse.errorSizeEquals(1)
    errorResponse.errors[0].equalsTo(
        ErrorCode.COLLECTION_WITH_NAME_ALREADY_EXISTS,
        String.format(ErrorCode.COLLECTION_WITH_NAME_ALREADY_EXISTS.message, newCollectionName))
  }
}

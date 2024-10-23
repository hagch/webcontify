package io.webcontify.backend.collections.apis.field

import helpers.asserts.equalsTo
import helpers.asserts.errorSizeEquals
import helpers.asserts.instanceEquals
import helpers.asserts.timestampNotNull
import helpers.setups.api.ApiIntegrationTestSetup
import helpers.suppliers.CollectionFieldApiCreateRequestSupplier.Companion.DECIMAL_FIELD
import helpers.suppliers.CollectionFieldApiCreateRequestSupplier.Companion.FIELD_WITH_INVALID_NAME
import helpers.suppliers.CollectionFieldApiCreateRequestSupplier.Companion.NEW_FIELD
import helpers.suppliers.CollectionFieldApiCreateRequestSupplier.Companion.NEW_PRIMARY_FIELD
import io.restassured.module.mockmvc.kotlin.extensions.Extract
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.models.errors.ErrorResponse
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql

class CreateFieldApiIntegrationTest : ApiIntegrationTestSetup() {

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(CreateField) should create field`() {
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(NEW_FIELD)
    } When
        {
          post("$COLLECTIONS_PATH/1/fields")
        } Then
        {
          status(HttpStatus.OK)
          body("collectionId", equalTo(1))
          body("name", equalTo(NEW_FIELD.name))
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(CreateField) should return not found if collection does not exist`() {
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(NEW_FIELD)
        } When
            {
              post("$COLLECTIONS_PATH/5/fields")
            } Then
            {
              status(HttpStatus.NOT_FOUND)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.instanceEquals("/$COLLECTIONS_PATH/5/fields")
    errorResponse.errorSizeEquals(1)
    errorResponse.timestampNotNull()
    errorResponse.errors[0].equalsTo(
        ErrorCode.COLLECTION_NOT_FOUND, String.format(ErrorCode.COLLECTION_NOT_FOUND.message, 5))
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(CreateField) should return bad request if field name is invalid`() {
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(FIELD_WITH_INVALID_NAME)
        } When
            {
              post("$COLLECTIONS_PATH/1/fields")
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.instanceEquals("/$COLLECTIONS_PATH/1/fields")
    errorResponse.errorSizeEquals(1)
    errorResponse.timestampNotNull()
    errorResponse.errors[0].equalsTo(ErrorCode.INVALID_NAME, ErrorCode.INVALID_NAME.message)
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(CreateField) should return field already exists if field with name for collection already exists`() {
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(DECIMAL_FIELD)
        } When
            {
              post("$COLLECTIONS_PATH/1/fields")
            } Then
            {
              status(HttpStatus.CONFLICT)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.instanceEquals("/$COLLECTIONS_PATH/1/fields")
    errorResponse.errorSizeEquals(1)
    errorResponse.timestampNotNull()
    errorResponse.errors[0].equalsTo(
        ErrorCode.FIELD_WITH_NAME_ALREADY_EXISTS,
        String.format(ErrorCode.FIELD_WITH_NAME_ALREADY_EXISTS.message, DECIMAL_FIELD.name, 1))
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(CreateField) should return error on creating a primary key field`() {
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(NEW_PRIMARY_FIELD)
        } When
            {
              post("$COLLECTIONS_PATH/1/fields")
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.instanceEquals("/$COLLECTIONS_PATH/1/fields")
    errorResponse.errorSizeEquals(1)
    errorResponse.timestampNotNull()
    errorResponse.errors[0].equalsTo(
        ErrorCode.UNSUPPORTED_FIELD_OPERATION, ErrorCode.UNSUPPORTED_FIELD_OPERATION.message)
  }
}

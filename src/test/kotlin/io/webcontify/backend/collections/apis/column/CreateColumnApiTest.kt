package io.webcontify.backend.collections.apis.column

import helpers.asserts.equalsTo
import helpers.asserts.errorSizeEquals
import helpers.asserts.instanceEquals
import helpers.asserts.timestampNotNull
import helpers.setups.api.ApiTestSetup
import helpers.suppliers.CollectionColumnApiCreateRequestSupplier.Companion.COLUMN_WITH_INVALID_NAME
import helpers.suppliers.CollectionColumnApiCreateRequestSupplier.Companion.DECIMAL_COLUMN
import helpers.suppliers.CollectionColumnApiCreateRequestSupplier.Companion.NEW_COLUMN
import helpers.suppliers.CollectionColumnApiCreateRequestSupplier.Companion.NEW_PRIMARY_COLUMN
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

class CreateColumnApiTest : ApiTestSetup() {

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(CreateColumn) should create column`() {
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(NEW_COLUMN)
    } When
        {
          post("$COLLECTIONS_PATH/1/columns")
        } Then
        {
          status(HttpStatus.OK)
          body("collectionId", equalTo(1))
          body("name", equalTo(NEW_COLUMN.name))
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(CreateColumn) should return not found if collection does not exist`() {
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(NEW_COLUMN)
        } When
            {
              post("$COLLECTIONS_PATH/5/columns")
            } Then
            {
              status(HttpStatus.NOT_FOUND)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.instanceEquals("/$COLLECTIONS_PATH/5/columns")
    errorResponse.errorSizeEquals(1)
    errorResponse.timestampNotNull()
    errorResponse.errors[0].equalsTo(
        ErrorCode.COLLECTION_NOT_FOUND, String.format(ErrorCode.COLLECTION_NOT_FOUND.message, 5))
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(CreateColumn) should return bad request if column name is invalid`() {
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(COLUMN_WITH_INVALID_NAME)
        } When
            {
              post("$COLLECTIONS_PATH/1/columns")
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.instanceEquals("/$COLLECTIONS_PATH/1/columns")
    errorResponse.errorSizeEquals(1)
    errorResponse.timestampNotNull()
    errorResponse.errors[0].equalsTo(ErrorCode.INVALID_NAME, ErrorCode.INVALID_NAME.message)
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(CreateColumn) should return column already exists if column with name for collection already exists`() {
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(DECIMAL_COLUMN)
        } When
            {
              post("$COLLECTIONS_PATH/1/columns")
            } Then
            {
              status(HttpStatus.CONFLICT)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.instanceEquals("/$COLLECTIONS_PATH/1/columns")
    errorResponse.errorSizeEquals(1)
    errorResponse.timestampNotNull()
    errorResponse.errors[0].equalsTo(
        ErrorCode.COLUMN_WITH_NAME_ALREADY_EXISTS,
        String.format(ErrorCode.COLUMN_WITH_NAME_ALREADY_EXISTS.message, DECIMAL_COLUMN.name, 1))
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(CreateColumn) should return error on creating a primary key column`() {
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(NEW_PRIMARY_COLUMN)
        } When
            {
              post("$COLLECTIONS_PATH/1/columns")
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.instanceEquals("/$COLLECTIONS_PATH/1/columns")
    errorResponse.errorSizeEquals(1)
    errorResponse.timestampNotNull()
    errorResponse.errors[0].equalsTo(
        ErrorCode.UNSUPPORTED_COLUMN_OPERATION, ErrorCode.UNSUPPORTED_COLUMN_OPERATION.message)
  }
}

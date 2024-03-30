package io.webcontify.backend.collections.apis.column

import helpers.asserts.equalsTo
import helpers.asserts.errorSizeEquals
import helpers.asserts.instanceEquals
import helpers.asserts.timestampNotNull
import helpers.setups.api.ApiTestSetup
import helpers.suppliers.CollectionColumnApiUpdateRequestSupplier.Companion.DECIMAL_COLUMN_NAME_CHANGED
import helpers.suppliers.CollectionColumnApiUpdateRequestSupplier.Companion.DECIMAL_COLUMN_TYPE_CHANGED
import helpers.suppliers.CollectionColumnApiUpdateRequestSupplier.Companion.EXISTING_COLUMN_NAME
import helpers.suppliers.CollectionColumnApiUpdateRequestSupplier.Companion.NUMBER_COLUMN_PRIMARY_CHANGED
import helpers.suppliers.CollectionColumnApiUpdateRequestSupplier.Companion.NUMBER_PRIMARY_COLUMN_NAME_CHANGED
import helpers.suppliers.CollectionColumnApiUpdateRequestSupplier.Companion.RELATED_COLUMN_CHANGED
import io.restassured.module.mockmvc.kotlin.extensions.Extract
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.models.errors.ErrorResponse
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql

class UpdateColumnApiTest : ApiTestSetup() {

  @Test
  @Sql("./../cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(UpdateColumn) should update column for collection`() {
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON)
      body(DECIMAL_COLUMN_NAME_CHANGED.second)
    } When
        {
          put("$COLLECTIONS_PATH/1/columns/${DECIMAL_COLUMN_NAME_CHANGED.first}")
        } Then
        {
          status(HttpStatus.OK)
          body("name", equalTo(DECIMAL_COLUMN_NAME_CHANGED.second.name))
          body("displayName", equalTo(DECIMAL_COLUMN_NAME_CHANGED.second.displayName))
        }
  }

  @Test
  @Sql("./../cleanup.sql", "./../collection-with-relation.sql")
  fun `(UpdateColumn) should return error if name is used in relation`() {
    val path = "$COLLECTIONS_PATH/2/columns/${RELATED_COLUMN_CHANGED.first}"
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON)
          body(RELATED_COLUMN_CHANGED.second)
        } When
            {
              put(path)
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.errorSizeEquals(1)
    errorResponse.instanceEquals("/$path")
    errorResponse.timestampNotNull()
    errorResponse.errors[0].equalsTo(
        ErrorCode.COLUMN_USED_IN_RELATION,
        String.format(ErrorCode.COLUMN_USED_IN_RELATION.message, RELATED_COLUMN_CHANGED.first, 2))
  }

  @Test
  @Sql("./../cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(UpdateColumn) should return error if column with new name already exists`() {
    val path = "$COLLECTIONS_PATH/1/columns/${EXISTING_COLUMN_NAME.first}"
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON)
          body(EXISTING_COLUMN_NAME.second)
        } When
            {
              put(path)
            } Then
            {
              status(HttpStatus.CONFLICT)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.errorSizeEquals(1)
    errorResponse.instanceEquals("/$path")
    errorResponse.timestampNotNull()
    errorResponse.errors[0].equalsTo(
        ErrorCode.COLUMN_WITH_NAME_ALREADY_EXISTS,
        String.format(
            ErrorCode.COLUMN_WITH_NAME_ALREADY_EXISTS.message, EXISTING_COLUMN_NAME.second, 1))
  }

  @Test
  @Sql("./../cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(UpdateColumn) should throw error on changing primary key`() {
    val path = "$COLLECTIONS_PATH/1/columns/${NUMBER_COLUMN_PRIMARY_CHANGED.first}"
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON)
          body(NUMBER_COLUMN_PRIMARY_CHANGED.second)
        } When
            {
              put(path)
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.errorSizeEquals(1)
    errorResponse.instanceEquals("/$path")
    errorResponse.timestampNotNull()
    errorResponse.errors[0].equalsTo(
        ErrorCode.UNSUPPORTED_COLUMN_OPERATION, ErrorCode.UNSUPPORTED_COLUMN_OPERATION.message)
  }

  @Test
  @Sql("./../cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(UpdateColumn) should throw error on changing primary key column name`() {
    val path = "$COLLECTIONS_PATH/1/columns/${NUMBER_PRIMARY_COLUMN_NAME_CHANGED.first}"
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON)
          body(NUMBER_PRIMARY_COLUMN_NAME_CHANGED.second)
        } When
            {
              put(path)
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.errorSizeEquals(1)
    errorResponse.instanceEquals("/$path")
    errorResponse.timestampNotNull()
    errorResponse.errors[0].equalsTo(
        ErrorCode.UNSUPPORTED_COLUMN_OPERATION, ErrorCode.UNSUPPORTED_COLUMN_OPERATION.message)
  }

  @Test
  @Sql("./../cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(UpdateColumn) should throw error on changing type`() {
    val path = "$COLLECTIONS_PATH/1/columns/${DECIMAL_COLUMN_TYPE_CHANGED.first}"
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON)
          body(DECIMAL_COLUMN_TYPE_CHANGED.second)
        } When
            {
              put(path)
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.errorSizeEquals(1)
    errorResponse.instanceEquals("/$path")
    errorResponse.timestampNotNull()
    errorResponse.errors[0].equalsTo(
        ErrorCode.UNSUPPORTED_COLUMN_OPERATION, ErrorCode.UNSUPPORTED_COLUMN_OPERATION.message)
  }
}

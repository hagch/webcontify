package io.webcontify.backend.collections.apis.column

import helpers.asserts.equalsTo
import helpers.asserts.errorSizeEquals
import helpers.asserts.instanceEquals
import helpers.asserts.timestampNotNull
import helpers.setups.api.ApiTestSetup
import helpers.suppliers.CollectionColumnApiCreateRequestSupplier.Companion.DECIMAL_COLUMN
import helpers.suppliers.CollectionColumnApiCreateRequestSupplier.Companion.NUMBER_PRIMARY_COLUMN
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
import org.springframework.test.context.jdbc.Sql

class DeleteColumnApiTest : ApiTestSetup() {

  @Test
  @Sql("./../cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(DeleteColumn) should delete column for collection`() {
    Given { mockMvc(mockMvc) } When
        {
          delete("$COLLECTIONS_PATH/1/columns/${DECIMAL_COLUMN.name}")
        } Then
        {
          status(HttpStatus.NO_CONTENT)
        }
  }

  @Test
  @Sql("./../cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(DeleteColumn) should return not found if column does not exist`() {
    val path = "$COLLECTIONS_PATH/1/columns/does_not_exist"
    val errorResponse =
        Given { mockMvc(mockMvc) } When
            {
              delete(path)
            } Then
            {
              status(HttpStatus.NOT_FOUND)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.timestampNotNull()
    errorResponse.instanceEquals("/$path")
    errorResponse.errorSizeEquals(1)
    errorResponse.errors[0].equalsTo(
        ErrorCode.COLUMN_NOT_FOUND,
        String.format(ErrorCode.COLUMN_NOT_FOUND.message, 1, "does_not_exist"))
  }

  @Test
  @Sql("./../cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(DeleteColumn) should return not found if collection does not exist`() {
    val path = "$COLLECTIONS_PATH/5/columns/number_column"
    val errorResponse =
        Given { mockMvc(mockMvc) } When
            {
              delete(path)
            } Then
            {
              status(HttpStatus.NOT_FOUND)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.timestampNotNull()
    errorResponse.instanceEquals("/$path")
    errorResponse.errorSizeEquals(1)
    errorResponse.errors[0].equalsTo(
        ErrorCode.COLLECTION_NOT_FOUND, String.format(ErrorCode.COLLECTION_NOT_FOUND.message, 1))
  }

  @Test
  @Sql("./../cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(DeleteColumn) should return bad request if column is primary key`() {
    val path = "$COLLECTIONS_PATH/1/columns/${NUMBER_PRIMARY_COLUMN.name}"
    val errorResponse =
        Given { mockMvc(mockMvc) } When
            {
              delete(path)
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.timestampNotNull()
    errorResponse.instanceEquals("/$path")
    errorResponse.errorSizeEquals(1)
    errorResponse.errors[0].equalsTo(
        ErrorCode.COLUMN_IS_PRIMARY_COLUMN,
        String.format(ErrorCode.COLUMN_IS_PRIMARY_COLUMN.message, 1, NUMBER_PRIMARY_COLUMN.name))
  }

  @Test
  @Sql("./../cleanup.sql", "./../collection-with-relation.sql")
  fun `(DeleteColumn) should return error if column is used in relation`() {
    val path = "$COLLECTIONS_PATH/2/columns/related_column"
    val errorResponse =
        Given { mockMvc(mockMvc) } When
            {
              delete(path)
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.timestampNotNull()
    errorResponse.instanceEquals("/$path")
    errorResponse.errorSizeEquals(1)
    errorResponse.errors[0].equalsTo(
        ErrorCode.COLUMN_USED_IN_RELATION,
        String.format(ErrorCode.COLUMN_USED_IN_RELATION.message, 2, "related_column"))
  }
}

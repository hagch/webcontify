package io.webcontify.backend.collections.apis.field

import helpers.asserts.equalsTo
import helpers.asserts.errorSizeEquals
import helpers.asserts.instanceEquals
import helpers.asserts.timestampNotNull
import helpers.setups.api.ApiTestSetup
import helpers.suppliers.CollectionFieldApiCreateRequestSupplier.Companion.NUMBER_PRIMARY_FIELD
import io.restassured.module.mockmvc.kotlin.extensions.Extract
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.models.errors.ErrorResponse
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql

class DeleteFieldApiTest : ApiTestSetup() {

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(DeleteField) should delete field for collection`() {
    Given { mockMvc(mockMvc) } When
        {
          delete("$COLLECTIONS_PATH/1/fields/3")
        } Then
        {
          status(HttpStatus.NO_CONTENT)
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(DeleteField) should return not found if field does not exist`() {
    val path = "$COLLECTIONS_PATH/1/fields/33"
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
        ErrorCode.FIELD_NOT_FOUND, String.format(ErrorCode.FIELD_NOT_FOUND.message, 1, 33))
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(DeleteField) should return not found if collection does not exist`() {
    val path = "$COLLECTIONS_PATH/5/fields/1"
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
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(DeleteField) should return bad request if field is primary key`() {
    val path = "$COLLECTIONS_PATH/1/fields/2"
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
        ErrorCode.FIELD_IS_PRIMARY_FIELD,
        String.format(ErrorCode.FIELD_IS_PRIMARY_FIELD.message, 1, NUMBER_PRIMARY_FIELD.name))
  }

  @Test
  @Sql("/cleanup.sql", "./../collection-with-relation.sql")
  fun `(DeleteField) should return error if field is used in relation`() {
    val path = "$COLLECTIONS_PATH/2/fields/3"
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
        ErrorCode.FIELD_USED_IN_RELATION,
        String.format(ErrorCode.FIELD_USED_IN_RELATION.message, 2, 3))
  }
}

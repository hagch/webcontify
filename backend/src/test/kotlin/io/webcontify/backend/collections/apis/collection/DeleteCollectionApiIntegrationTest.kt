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
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.models.errors.ErrorResponse
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql

class DeleteCollectionApiIntegrationTest : ApiIntegrationTestSetup() {

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(DeleteCollectionById) should delete collection`() {
    Given { mockMvc(mockMvc) } When
        {
          delete("$COLLECTIONS_PATH/1")
        } Then
        {
          status(HttpStatus.NO_CONTENT)
        }

    Given { mockMvc(mockMvc) } When
        {
          get("$COLLECTIONS_PATH/1")
        } Then
        {
          status(HttpStatus.NOT_FOUND)
        }
  }

  @Test
  @Sql("/cleanup.sql")
  fun `(DeleteCollectionById) should return error on id not found`() {
    val error =
        Given { mockMvc(mockMvc) } When
            {
              delete("$COLLECTIONS_PATH/1")
            } Then
            {
              status(HttpStatus.NOT_FOUND)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }

    error.instanceEquals("/$COLLECTIONS_PATH/1")
    error.timestampNotNull()
    error.errorSizeEquals(1)
    error.errors[0].equalsTo(
        ErrorCode.COLLECTION_NOT_FOUND, String.format(ErrorCode.COLLECTION_NOT_FOUND.message, 1))
  }
}

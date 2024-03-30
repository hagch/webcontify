package io.webcontify.backend.collections.apis.collection

import helpers.asserts.equalsTo
import helpers.asserts.errorSizeEquals
import helpers.asserts.instanceEquals
import helpers.asserts.timestampNotNull
import helpers.setups.api.ApiTestSetup
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

class GetCollectionApiTest : ApiTestSetup() {

  @Test
  @Sql("./../cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(GetCollectionById) should return collection`() {
    Given { mockMvc(mockMvc) } When
        {
          get("$COLLECTIONS_PATH/1")
        } Then
        {
          status(HttpStatus.OK)
          body("id", equalTo(1))
        }
  }

  @Test
  @Sql("./../cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(GetCollectionById) should return error on id not found`() {
    val collectionId = -1

    val errorResponse =
        Given { mockMvc(mockMvc) } When
            {
              get("$COLLECTIONS_PATH/$collectionId")
            } Then
            {
              status(HttpStatus.NOT_FOUND)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }

    errorResponse.instanceEquals("/$COLLECTIONS_PATH/$collectionId")
    errorResponse.timestampNotNull()
    errorResponse.errorSizeEquals(1)
    errorResponse.errors[0].equalsTo(
        ErrorCode.COLLECTION_NOT_FOUND,
        String.format(ErrorCode.COLLECTION_NOT_FOUND.message, collectionId))
  }

  @Test
  @Sql("./../cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(GetAllCollections) should return list of collections`() {
    Given { mockMvc(mockMvc) } When
        {
          get(COLLECTIONS_PATH)
        } Then
        {
          status(HttpStatus.OK)
          body("", hasSize<MutableCollection<Map<String, Any>>>(equalTo(2)))
        }
  }

  @Test
  @Sql("./../cleanup.sql")
  fun `(GetAllCollections) should return empty list of collections if no collection exists`() {
    Given { mockMvc(mockMvc) } When
        {
          get(COLLECTIONS_PATH)
        } Then
        {
          status(HttpStatus.OK)
          body("", empty<Map<String, Any>>())
        }
  }
}

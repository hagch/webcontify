package io.webcontify.backend.collections.apis.column

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
import org.hamcrest.Matchers
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql

class GetColumnApiTest : ApiTestSetup() {

  @Test
  @Sql("./../cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(GetColumnByName) should return column`() {
    Given { mockMvc(mockMvc) } When
        {
          get("$COLLECTIONS_PATH/1/columns/number_column")
        } Then
        {
          status(HttpStatus.OK)
          body("collectionId", Matchers.equalTo(1))
          body("name", Matchers.equalTo("number_column"))
        }
  }

  @Test
  @Sql("./../cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(GetColumnByName) should return not found if column does not exist`() {
    val path = "$COLLECTIONS_PATH/1/columns/does_not_exist"
    val error =
        Given { mockMvc(mockMvc) } When
            {
              get(path)
            } Then
            {
              status(HttpStatus.NOT_FOUND)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    error.timestampNotNull()
    error.instanceEquals("/$path")
    error.errorSizeEquals(1)
    error.errors[0].equalsTo(
        ErrorCode.COLUMN_NOT_FOUND,
        String.format(ErrorCode.COLUMN_NOT_FOUND.message, "does_not_exist", "1"))
  }

  @Test
  @Sql("./../cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(GetColumnByName) should return column not found if collection does not exist`() {
    val path = "$COLLECTIONS_PATH/5/columns/does_not_exist"
    val error =
        Given { mockMvc(mockMvc) } When
            {
              get(path)
            } Then
            {
              status(HttpStatus.NOT_FOUND)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    error.timestampNotNull()
    error.instanceEquals("/$path")
    error.errorSizeEquals(1)
    error.errors[0].equalsTo(
        ErrorCode.COLUMN_NOT_FOUND,
        String.format(ErrorCode.COLUMN_NOT_FOUND.message, "does_not_exist", "5"))
  }

  @Test
  @Sql("./../cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(GetColumnsForCollection) should return all columns for collection`() {
    val path = "$COLLECTIONS_PATH/1/columns"
    val columns =
        Given { mockMvc(mockMvc) } When
            {
              get(path)
            } Then
            {
              status(HttpStatus.OK)
              body("", hasSize<MutableCollection<Map<String, Any>>>(equalTo(6)))
            } Extract
            {
              body().jsonPath().getList<Map<String, Any>>("")
            }
    assertTrue(columns.stream().allMatch { it["collectionId"] == 1 })
  }

  @Test
  @Sql("./../cleanup.sql", "./../collections-with-all-column-types.sql")
  fun `(GetColumnsForCollection) should return empty list if collection does not exist`() {
    val path = "$COLLECTIONS_PATH/5/columns"
    Given { mockMvc(mockMvc) } When
        {
          get(path)
        } Then
        {
          status(HttpStatus.OK)
          body("", empty<MutableCollection<Map<String, Any>>>())
        }
  }
}

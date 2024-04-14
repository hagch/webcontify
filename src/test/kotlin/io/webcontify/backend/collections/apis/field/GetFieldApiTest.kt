package io.webcontify.backend.collections.apis.field

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

class GetFieldApiTest : ApiTestSetup() {

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(GetFieldByName) should return field`() {
    Given { mockMvc(mockMvc) } When
        {
          get("$COLLECTIONS_PATH/1/fields/number_field")
        } Then
        {
          status(HttpStatus.OK)
          body("collectionId", Matchers.equalTo(1))
          body("name", Matchers.equalTo("number_field"))
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(GetFieldByName) should return not found if field does not exist`() {
    val path = "$COLLECTIONS_PATH/1/fields/does_not_exist"
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
        ErrorCode.FIELD_NOT_FOUND,
        String.format(ErrorCode.FIELD_NOT_FOUND.message, "does_not_exist", "1"))
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(GetFieldByName) should return field not found if collection does not exist`() {
    val path = "$COLLECTIONS_PATH/5/fields/does_not_exist"
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
        ErrorCode.FIELD_NOT_FOUND,
        String.format(ErrorCode.FIELD_NOT_FOUND.message, "does_not_exist", "5"))
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(GetFieldsForCollection) should return all fields for collection`() {
    val path = "$COLLECTIONS_PATH/1/fields"
    val fields =
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
    assertTrue(fields.stream().allMatch { it["collectionId"] == 1 })
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(GetFieldsForCollection) should return empty list if collection does not exist`() {
    val path = "$COLLECTIONS_PATH/5/fields"
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

package io.webcontify.backend.collections.apis.item.constraints

import helpers.asserts.*
import helpers.setups.api.ApiIntegrationTestSetup
import io.restassured.module.mockmvc.kotlin.extensions.Extract
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.models.errors.ErrorResponse
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import org.hamcrest.CoreMatchers.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql

class BooleanConstraintsApiIntegrationTest : ApiIntegrationTestSetup() {

  @Test
  @Sql("/cleanup.sql", "./../../boolean-constraints-collections.sql")
  fun `(CreateItem) should create item with boolean value in constraints`() {
    val item =
        mapOf(
            "booleanField" to true,
        )
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(item)
    } When
        {
          post("$COLLECTIONS_PATH/1/items")
        } Then
        {
          status(HttpStatus.CREATED)
          body("booleanField", equalTo(true))
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../boolean-constraints-collections.sql")
  fun `(CreateItem) should create item with default value on boolean value is null`() {
    val item =
        mapOf(
            "booleanField" to null,
        )
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(item)
    } When
        {
          post("$COLLECTIONS_PATH/1/items")
        } Then
        {
          status(HttpStatus.CREATED)
          body("booleanField", equalTo(true))
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../boolean-constraints-collections.sql")
  fun `(CreateItem) should create item with default value on boolean value is not contained`() {
    val item: Map<String, Any> = mapOf()
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(item)
    } When
        {
          post("$COLLECTIONS_PATH/1/items")
        } Then
        {
          status(HttpStatus.CREATED)
          body("booleanField", equalTo(true))
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../boolean-constraints-collections.sql")
  fun `(CreateItem) should not create boolean field because value is not inValues`() {
    val item =
        mapOf(
            "booleanField" to false,
        )
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(item)
        } When
            {
              post("$COLLECTIONS_PATH/1/items")
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.instanceEquals("/$COLLECTIONS_PATH/1/items")
    errorResponse.errorSizeEquals(1)
    errorResponse.timestampNotNull()
    errorResponse.errors[0].codeEquals(ErrorCode.INVALID_VALUE_PASSED)
    errorResponse.errors[0].messageContains(
        "Value 11 for field boolean_field is invalid, please check if value complies to configuration")
  }

  @Test
  @Sql("/cleanup.sql", "./../../boolean-constraints-collections.sql")
  fun `(CreateItem) should create boolean field with value null`() {
    var item =
        mapOf(
            "booleanField" to null,
        )
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(item)
    } When
        {
          post("$COLLECTIONS_PATH/2/items")
        } Then
        {
          status(HttpStatus.CREATED)
          body("booleanField", nullValue())
          body("uuidField", notNullValue())
        }
    item = mapOf()
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(item)
    } When
        {
          post("$COLLECTIONS_PATH/2/items")
        } Then
        {
          status(HttpStatus.CREATED)
          body("booleanField", nullValue())
          body("uuidField", notNullValue())
        }
  }
}

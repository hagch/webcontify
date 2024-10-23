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

class UUIDConstraintsApiIntegrationTest : ApiIntegrationTestSetup() {

  @Test
  @Sql("/cleanup.sql", "./../../uuid-constraints-collections.sql")
  fun `(CreateItem) should create item with uuid value in constraints`() {
    val item =
        mapOf(
            "uuidField" to "9b5ae74f-49e4-4a6f-bbf0-c6b622c77f63",
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
          body("uuidField", equalTo("9b5ae74f-49e4-4a6f-bbf0-c6b622c77f63"))
          body("primaryField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../uuid-constraints-collections.sql")
  fun `(CreateItem) should create item with default value on uuid value is null`() {
    val item =
        mapOf(
            "uuidField" to null,
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
          body("uuidField", equalTo("97dafc8c-77f6-44ac-beca-fa5dbbf3346f"))
          body("primaryField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../uuid-constraints-collections.sql")
  fun `(CreateItem) should create item with default value on uuid value is not contained`() {
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
          body("uuidField", equalTo("97dafc8c-77f6-44ac-beca-fa5dbbf3346f"))
          body("primaryField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../uuid-constraints-collections.sql")
  fun `(CreateItem) should not create uuid field because value is not inValues`() {
    val item =
        mapOf(
            "uuidField" to "0572648b-8524-4175-b916-bdd36a661a60",
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
        "Value 0572648b-8524-4175-b916-bdd36a661a60 for field uuid_field is invalid, please check if value complies to configuration")
  }

  @Test
  @Sql("/cleanup.sql", "./../../uuid-constraints-collections.sql")
  fun `(CreateItem) should create uuid field with value null`() {
    var item =
        mapOf(
            "uuidField" to null,
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
          body("uuidField", nullValue())
          body("primaryField", notNullValue())
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
          body("uuidField", nullValue())
          body("primaryField", notNullValue())
        }
  }
}

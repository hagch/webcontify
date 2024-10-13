package io.webcontify.backend.collections.apis.item.constraints

import helpers.asserts.*
import helpers.setups.api.ApiTestSetup
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

class ItemNumberConstrainsApiTest : ApiTestSetup() {

  @Test
  @Sql("/cleanup.sql", "./../../number-constraints-collections.sql")
  fun `(CreateItem) should create item with number value in constraints`() {
    val item =
        mapOf(
            "numberField" to 20,
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
          body("numberField", equalTo(20))
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../number-constraints-collections.sql")
  fun `(CreateItem) should create item with default value on number value is null`() {
    val item =
        mapOf(
            "numberField" to null,
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
          body("numberField", equalTo(10))
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../number-constraints-collections.sql")
  fun `(CreateItem) should create item with default value on number value is not contained`() {
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
          body("numberField", equalTo(10))
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../number-constraints-collections.sql")
  fun `(CreateItem) should not create number field because value is not inValues`() {
    val item =
        mapOf(
            "numberField" to 11,
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
        "Value 11 for field number_field is invalid, please check if value complies to configuration")
  }

  @Test
  @Sql("/cleanup.sql", "./../../number-constraints-collections.sql")
  fun `(CreateItem) should create number field with value null`() {
    var item =
        mapOf(
            "numberField" to null,
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
          body("numberField", nullValue())
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
          body("numberField", nullValue())
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../number-constraints-collections.sql")
  fun `(CreateItem) should not create item if number field is out of greaterThan or lowerThan`() {
    var item =
        mapOf(
            "numberField" to 10,
        )
    var errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(item)
        } When
            {
              post("$COLLECTIONS_PATH/4/items")
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.instanceEquals("/$COLLECTIONS_PATH/4/items")
    errorResponse.errorSizeEquals(1)
    errorResponse.timestampNotNull()
    errorResponse.errors[0].codeEquals(ErrorCode.INVALID_VALUE_PASSED)
    errorResponse.errors[0].messageContains(
        "Value 10 for field number_field is invalid, please check if value complies to configuration")

    item =
        mapOf(
            "numberField" to 13,
        )
    errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(item)
        } When
            {
              post("$COLLECTIONS_PATH/4/items")
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.instanceEquals("/$COLLECTIONS_PATH/4/items")
    errorResponse.errorSizeEquals(1)
    errorResponse.timestampNotNull()
    errorResponse.errors[0].codeEquals(ErrorCode.INVALID_VALUE_PASSED)
    errorResponse.errors[0].messageContains(
        "Value null for field number_field is invalid, please check if value complies to configuration")
  }

  @Test
  @Sql("/cleanup.sql", "./../../number-constraints-collections.sql")
  fun `(CreateItem) should create number field if value is between lower and greater then`() {
    val item =
        mapOf(
            "numberField" to 11,
        )
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(item)
    } When
        {
          post("$COLLECTIONS_PATH/4/items")
        } Then
        {
          status(HttpStatus.CREATED)
          body("numberField", equalTo(11))
          body("uuidField", notNullValue())
        }
  }
}

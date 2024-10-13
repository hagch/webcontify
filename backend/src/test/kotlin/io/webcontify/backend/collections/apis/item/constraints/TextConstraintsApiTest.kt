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

class TextConstraintsApiTest : ApiTestSetup() {

  @Test
  @Sql("/cleanup.sql", "./../../text-constraints-collections.sql")
  fun `(CreateItem) should create item with text value in constraints`() {
    val item =
        mapOf(
            "textField" to "test",
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
          body("textField", equalTo("test"))
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../text-constraints-collections.sql")
  fun `(CreateItem) should create item with default value on text value is null`() {
    val item =
        mapOf(
            "textField" to null,
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
          body("textField", equalTo("test"))
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../text-constraints-collections.sql")
  fun `(CreateItem) should create item with default value on text value is not contained`() {
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
          body("textField", equalTo("test"))
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../text-constraints-collections.sql")
  fun `(CreateItem) should not create text field because value is not inValues`() {
    val item =
        mapOf(
            "textField" to "notInValues",
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
        "Value notInValues for field text_field is invalid, please check if value complies to configuration")
  }

  @Test
  @Sql("/cleanup.sql", "./../../text-constraints-collections.sql")
  fun `(CreateItem) should create text field with value null`() {
    var item =
        mapOf(
            "textField" to null,
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
          body("textField", nullValue())
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
          body("textField", nullValue())
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../text-constraints-collections.sql")
  fun `(CreateItem) should not create item if number field is out of max or min length`() {
    var item =
        mapOf(
            "textField" to "te",
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
        "Value te for field text_field is invalid, please check if value complies to configuration")

    item =
        mapOf(
            "textField" to "tester",
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
        "Value null for field text_field is invalid, please check if value complies to configuration")
  }

  @Test
  @Sql("/cleanup.sql", "./../../text-constraints-collections.sql")
  fun `(CreateItem) should create text field if value is in min and max length`() {
    val item =
        mapOf(
            "textField" to "teste",
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
          body("textField", equalTo("teste"))
          body("uuidField", notNullValue())
        }
    val item2 =
        mapOf(
            "textField" to "tes",
        )
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(item2)
    } When
        {
          post("$COLLECTIONS_PATH/4/items")
        } Then
        {
          status(HttpStatus.CREATED)
          body("textField", equalTo("tes"))
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../text-constraints-collections.sql")
  fun `(CreateItem) should create text field if value matches regex`() {
    val item =
        mapOf(
            "textField" to "test.test@gmail.com",
        )
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(item)
    } When
        {
          post("$COLLECTIONS_PATH/5/items")
        } Then
        {
          status(HttpStatus.CREATED)
          body("textField", equalTo("test.test@gmail.com"))
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../text-constraints-collections.sql")
  fun `(CreateItem) should not create text field if value does not match regex`() {
    val item =
        mapOf(
            "textField" to "test.testgmail.com",
        )
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(item)
        } When
            {
              post("$COLLECTIONS_PATH/5/items")
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.instanceEquals("/$COLLECTIONS_PATH/5/items")
    errorResponse.errorSizeEquals(1)
    errorResponse.timestampNotNull()
    errorResponse.errors[0].codeEquals(ErrorCode.INVALID_VALUE_PASSED)
    errorResponse.errors[0].messageContains(
        "Value test.testgmail.com for field text_field is invalid, please check if value complies to configuration")
  }
}

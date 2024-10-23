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

class DecimalConstraintsApiIntegrationTest : ApiIntegrationTestSetup() {

  @Test
  @Sql("/cleanup.sql", "./../../decimal-constraints-collections.sql")
  fun `(CreateItem) should create item with decimal value in constraints`() {
    val item =
        mapOf(
            "decimalField" to 20.1,
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
          body("decimalField", equalTo(20.1F))
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../decimal-constraints-collections.sql")
  fun `(CreateItem) should create item with default value on decimal value is null`() {
    val item =
        mapOf(
            "decimalField" to null,
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
          body("decimalField", equalTo(81.1F))
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../decimal-constraints-collections.sql")
  fun `(CreateItem) should create item with default value on decimal value is not contained`() {
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
          body("decimalField", equalTo(81.1F))
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../decimal-constraints-collections.sql")
  fun `(CreateItem) should not create decimal field because value is not inValues`() {
    val item =
        mapOf(
            "decimalField" to 11.1,
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
        "Value 11 for field decimal_field is invalid, please check if value complies to configuration")
  }

  @Test
  @Sql("/cleanup.sql", "./../../decimal-constraints-collections.sql")
  fun `(CreateItem) should create decimal field with value null`() {
    var item =
        mapOf(
            "decimalField" to null,
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
          body("decimalField", nullValue())
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
          body("decimalField", nullValue())
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../decimal-constraints-collections.sql")
  fun `(CreateItem) should not create item if decimal field is out of greaterThan or lowerThan`() {
    var item =
        mapOf(
            "decimalField" to 10.1,
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
        "Value 10 for field decimal_field is invalid, please check if value complies to configuration")

    item =
        mapOf(
            "decimalField" to 13.2,
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
        "Value null for field decimal_field is invalid, please check if value complies to configuration")
  }

  @Test
  @Sql("/cleanup.sql", "./../../decimal-constraints-collections.sql")
  fun `(CreateItem) should create decimal field if value is between lower and greater then`() {
    val item =
        mapOf(
            "decimalField" to 10.6,
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
          body("decimalField", equalTo(10.6F))
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../decimal-constraints-collections.sql")
  fun `(CreateItem) should create decimal field if value is in precision and scale`() {
    val item =
        mapOf(
            "decimalField" to 10.65,
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
          body("decimalField", equalTo(10.65F))
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../decimal-constraints-collections.sql")
  fun `(CreateItem) should create decimal field with value rounded into scale`() {
    val item =
        mapOf(
            "decimalField" to 1.6545,
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
          body("decimalField", equalTo(1.655F))
          body("uuidField", notNullValue())
        }
  }
}

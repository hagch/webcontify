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

class TimestampConstraintsApiIntegrationTest : ApiIntegrationTestSetup() {

  @Test
  @Sql("/cleanup.sql", "./../../timestamp-constraints-collections.sql")
  fun `(CreateItem) should create item with timestamp value in constraints`() {
    val item =
        mapOf(
            "timestampField" to "2000-10-31T01:30:00",
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
          body("timestampField", equalTo("2000-10-31T01:30:00"))
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../timestamp-constraints-collections.sql")
  fun `(CreateItem) should create item with default value on timestamp value is null`() {
    val item =
        mapOf(
            "timestampField" to null,
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
          body("timestampField", equalTo("2000-10-31T01:30:00"))
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../timestamp-constraints-collections.sql")
  fun `(CreateItem) should create item with default value on timestamp value is not contained`() {
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
          body("timestampField", equalTo("2000-10-31T01:30:00"))
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../timestamp-constraints-collections.sql")
  fun `(CreateItem) should not create timestamp field because value is not inValues`() {
    val item =
        mapOf(
            "timestampField" to "2000-10-31T01:30:01",
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
        "Value 0572648b-8524-4175-b916-bdd36a661a60 for field timestamp_field is invalid, please check if value complies to configuration")
  }

  @Test
  @Sql("/cleanup.sql", "./../../timestamp-constraints-collections.sql")
  fun `(CreateItem) should create timestamp field with value null`() {
    var item =
        mapOf(
            "timestampField" to null,
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
          body("timestampField", nullValue())
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
          body("timestampField", nullValue())
          body("uuidField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../../timestamp-constraints-collections.sql")
  fun `(CreateItem) should not create item if number field is out of greaterThan or lowerThan`() {
    checkGreaterAndLowerConstraintViolatesOn("2000-10-31T01:30:00")
    checkGreaterAndLowerConstraintViolatesOn("2000-10-31T02:30:00")
    checkGreaterAndLowerConstraintViolatesOn("2000-10-31T02:31:00")
    checkGreaterAndLowerConstraintViolatesOn("2000-10-31T01:29:00")
  }

  private fun checkGreaterAndLowerConstraintViolatesOn(dateTime: String) {
    val item =
        mapOf(
            "timestampField" to dateTime,
        )
    val errorResponse =
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
        "Value $dateTime for field timestamp_field is invalid, please check if value complies to configuration")
  }

  @Test
  @Sql("/cleanup.sql", "./../../timestamp-constraints-collections.sql")
  fun `(CreateItem) should create timestamp field if value is between lower and greater then`() {
    val item =
        mapOf(
            "timestampField" to "2000-10-31T01:45:00",
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
          body("timestampField", equalTo("2000-10-31T01:45:00"))
          body("uuidField", notNullValue())
        }
  }
}

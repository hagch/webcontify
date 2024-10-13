package io.webcontify.backend.collections.apis.item

import helpers.asserts.*
import helpers.setups.api.ApiTestSetup
import io.restassured.module.mockmvc.kotlin.extensions.Extract
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.models.errors.ErrorResponse
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import java.util.*
import org.hamcrest.CoreMatchers.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql

class CreateItemApiTest : ApiTestSetup() {

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(CreateItem) should create item with all field type values with primary key number`() {
    val uuid = UUID.randomUUID().toString()
    val item =
        mapOf(
            "decimalField" to 123.01,
            "textField" to "Thats an text",
            "timestampField" to "2000-10-31T01:30:00",
            "booleanField" to true,
            "uuidField" to uuid,
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
          body("decimalField", equalTo(123.01f))
          body("textField", equalTo("Thats an text"))
          body("timestampField", equalTo("2000-10-31T01:30:00"))
          body("booleanField", equalTo(true))
          body("uuidField", equalTo(uuid))
          body("numberField", notNullValue())
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(CreateItem) should ignore primary number field with an value`() {
    val item =
        mapOf(
            "numberField" to 123,
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
          body("numberField", not(equalTo(123)))
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(CreateItem) should use primary uuid field with an value`() {
    val uuid = UUID.randomUUID().toString()
    val item =
        mapOf(
            "uuidField" to uuid,
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
          body("uuidField", equalTo(uuid))
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(CreateItem) should create item with all field type values with primary key uuid`() {
    val item =
        mapOf(
            "decimalField" to 123.01,
            "textField" to "Thats an text",
            "timestampField" to "2000-10-31T01:30:00",
            "booleanField" to true,
            "numberField" to 123,
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
          body("decimalField", equalTo(123.01f))
          body("textField", equalTo("Thats an text"))
          body("timestampField", equalTo("2000-10-31T01:30:00"))
          body("booleanField", equalTo(true))
          body("uuidField", notNullValue())
          body("numberField", equalTo(123))
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(CreateItem) should throw error on trying to create item with an mirror field`() {
    val item =
        mapOf(
            "decimalField" to 123.01,
            "textField" to "Thats an text",
            "timestampField" to "2000-10-31T01:30:00",
            "booleanField" to true,
            "mirrorField" to 123,
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
    errorResponse.timestampNotNull()
    errorResponse.instanceEquals("/$COLLECTIONS_PATH/1/items")
    errorResponse.errorSizeEquals(1)
    errorResponse.errors[0].equalsTo(
        ErrorCode.MIRROR_FIELD_INCLUDED, ErrorCode.MIRROR_FIELD_INCLUDED.message)
  }
}

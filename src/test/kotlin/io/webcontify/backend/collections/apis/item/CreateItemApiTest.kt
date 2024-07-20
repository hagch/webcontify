package io.webcontify.backend.collections.apis.item

import helpers.asserts.*
import helpers.setups.api.ApiTestSetup
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
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
            "decimal_field" to 123.01,
            "text_field" to "Thats an text",
            "timestamp_field" to "2000-10-31T01:30:00",
            "boolean_field" to true,
            "uuid_field" to uuid,
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
            "number_field" to 123,
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
            "uuid_field" to uuid,
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
            "decimal_field" to 123.01,
            "text_field" to "Thats an text",
            "timestamp_field" to "2000-10-31T01:30:00",
            "boolean_field" to true,
            "number_field" to 123,
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
}
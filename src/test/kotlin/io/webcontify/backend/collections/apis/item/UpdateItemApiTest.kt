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

class UpdateItemApiTest : ApiTestSetup() {

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(UpdateItem) should update item with all field type values with primary key number`() {
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
          patch("$COLLECTIONS_PATH/1/items/1")
        } Then
        {
          status(HttpStatus.OK)
          body("decimalField", equalTo(123.01f))
          body("textField", equalTo("Thats an text"))
          body("timestampField", equalTo("2000-10-31T01:30:00"))
          body("booleanField", equalTo(true))
          body("uuidField", equalTo(uuid))
          body("numberField", equalTo(1))
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(UpdateItem) should ignore primary number field with an value`() {
    val item =
        mapOf(
            "number_field" to 12,
            "text_field" to "Thats an text",
        )
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(item)
    } When
        {
          patch("$COLLECTIONS_PATH/1/items/1")
        } Then
        {
          status(HttpStatus.OK)
          body("numberField", not(equalTo(12)))
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(UpdateItem) should throw exception if no updatable fields are contained`() {
    val item =
        mapOf(
            "number_field" to 12,
        )
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(item)
        } When
            {
              patch("$COLLECTIONS_PATH/1/items/1")
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.timestampNotNull()
    errorResponse.instanceEquals("/$COLLECTIONS_PATH/1/items/1")
    errorResponse.errorSizeEquals(1)
    errorResponse.errors[0].equalsTo(
        ErrorCode.NO_FIELDS_TO_UPDATE,
        String.format(ErrorCode.NO_FIELDS_TO_UPDATE.message, "number_field= 1", "1"))
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(UpdateItem) should only update contained fields and return whole object`() {
    val item =
        mapOf(
            "decimal_field" to 123.01,
        )
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(item)
    } When
        {
          patch("$COLLECTIONS_PATH/1/items/1")
        } Then
        {
          status(HttpStatus.OK)
          body("decimalField", equalTo(123.01f))
          body("textField", nullValue())
          body("timestampField", nullValue())
          body("booleanField", nullValue())
          body("uuidField", nullValue())
          body("numberField", equalTo(1))
        }
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(UpdateItem) should throw error if not found`() {
    val item =
        mapOf(
            "decimal_field" to 123.01,
        )
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON_VALUE)
          body(item)
        } When
            {
              patch("$COLLECTIONS_PATH/1/items/4")
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.timestampNotNull()
    errorResponse.instanceEquals("/$COLLECTIONS_PATH/1/items/4")
    errorResponse.errorSizeEquals(1)
    errorResponse.errors[0].equalsTo(
        ErrorCode.ITEM_NOT_UPDATED,
        String.format(
            ErrorCode.ITEM_NOT_UPDATED.message, "(number_field= 1): decimal_field= 123.01", "1"))
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(UpdateItem) should ignore primary uuid field with an value`() {
    val uuid = UUID.randomUUID().toString()
    val item =
        mapOf(
            "uuid_field" to uuid,
            "text_field" to "Thats an text",
        )
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
      body(item)
    } When
        {
          patch("$COLLECTIONS_PATH/2/items/7e0a036d-0e99-42b3-a4e0-c55694ad04f4")
        } Then
        {
          status(HttpStatus.OK)
          body("uuidField", not(equalTo(uuid)))
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
          patch("$COLLECTIONS_PATH/2/items/7e0a036d-0e99-42b3-a4e0-c55694ad04f4")
        } Then
        {
          status(HttpStatus.OK)
          body("decimalField", equalTo(123.01f))
          body("textField", equalTo("Thats an text"))
          body("timestampField", equalTo("2000-10-31T01:30:00"))
          body("booleanField", equalTo(true))
          body("uuidField", notNullValue())
          body("numberField", equalTo(123))
        }
  }
}

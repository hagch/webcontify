package io.webcontify.backend.collections.apis.field

import helpers.asserts.equalsTo
import helpers.asserts.errorSizeEquals
import helpers.asserts.instanceEquals
import helpers.asserts.timestampNotNull
import helpers.setups.api.ApiTestSetup
import helpers.suppliers.CollectionFieldApiUpdateRequestSupplier.Companion.DECIMAL_FIELD_NAME_CHANGED
import helpers.suppliers.CollectionFieldApiUpdateRequestSupplier.Companion.DECIMAL_FIELD_TYPE_CHANGED
import helpers.suppliers.CollectionFieldApiUpdateRequestSupplier.Companion.EXISTING_FIELD_NAME
import helpers.suppliers.CollectionFieldApiUpdateRequestSupplier.Companion.NUMBER_FIELD_PRIMARY_CHANGED
import helpers.suppliers.CollectionFieldApiUpdateRequestSupplier.Companion.NUMBER_PRIMARY_FIELD_NAME_CHANGED
import helpers.suppliers.CollectionFieldApiUpdateRequestSupplier.Companion.RELATED_FIELD_CHANGED
import io.restassured.module.mockmvc.kotlin.extensions.Extract
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import io.webcontify.backend.collections.models.apis.WebContifyCollectionFieldApiUpdateRequest
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.models.errors.ErrorResponse
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql

class UpdateFieldApiTest : ApiTestSetup() {

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(UpdateField) should update field for collection`() {
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON)
      body(DECIMAL_FIELD_NAME_CHANGED.second)
    } When
        {
          put("$COLLECTIONS_PATH/1/fields/3")
        } Then
        {
          status(HttpStatus.OK)
          body("name", equalTo(DECIMAL_FIELD_NAME_CHANGED.second.name))
          body("displayName", equalTo(DECIMAL_FIELD_NAME_CHANGED.second.displayName))
        }
  }

  @Test
  @Disabled
  @Sql("/cleanup.sql", "./../collection-with-relation.sql")
  fun `(UpdateField) should return error if name is used in relation`() {
    val path = "$COLLECTIONS_PATH/2/fields/${RELATED_FIELD_CHANGED.first}"
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON)
          body(RELATED_FIELD_CHANGED.second)
        } When
            {
              put(path)
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.errorSizeEquals(1)
    errorResponse.instanceEquals("/$path")
    errorResponse.timestampNotNull()
    errorResponse.errors[0].equalsTo(
        ErrorCode.FIELD_USED_IN_RELATION,
        String.format(ErrorCode.FIELD_USED_IN_RELATION.message, RELATED_FIELD_CHANGED.first, 2))
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(UpdateField) should return error if field with new name already exists`() {
    val path = "$COLLECTIONS_PATH/1/fields/3"
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON)
          body(EXISTING_FIELD_NAME.second)
        } When
            {
              put(path)
            } Then
            {
              status(HttpStatus.CONFLICT)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.errorSizeEquals(1)
    errorResponse.instanceEquals("/$path")
    errorResponse.timestampNotNull()
    errorResponse.errors[0].equalsTo(
        ErrorCode.FIELD_WITH_NAME_ALREADY_EXISTS,
        String.format(
            ErrorCode.FIELD_WITH_NAME_ALREADY_EXISTS.message, EXISTING_FIELD_NAME.second, 1))
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(UpdateField) should throw error on changing primary key`() {
    val path = "$COLLECTIONS_PATH/1/fields/2"
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON)
          body(NUMBER_FIELD_PRIMARY_CHANGED.second)
        } When
            {
              put(path)
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.errorSizeEquals(1)
    errorResponse.instanceEquals("/$path")
    errorResponse.timestampNotNull()
    errorResponse.errors[0].equalsTo(
        ErrorCode.UNSUPPORTED_FIELD_OPERATION, ErrorCode.UNSUPPORTED_FIELD_OPERATION.message)
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(UpdateField) should throw error on changing primary key field name`() {
    val path = "$COLLECTIONS_PATH/1/fields/2"
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON)
          body(NUMBER_PRIMARY_FIELD_NAME_CHANGED.second)
        } When
            {
              put(path)
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.errorSizeEquals(1)
    errorResponse.instanceEquals("/$path")
    errorResponse.timestampNotNull()
    errorResponse.errors[0].equalsTo(
        ErrorCode.UNSUPPORTED_FIELD_OPERATION, ErrorCode.UNSUPPORTED_FIELD_OPERATION.message)
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(UpdateField) should throw error on changing type`() {
    val path = "$COLLECTIONS_PATH/1/fields/3"
    val errorResponse =
        Given {
          mockMvc(mockMvc)
          contentType(MediaType.APPLICATION_JSON)
          body(DECIMAL_FIELD_TYPE_CHANGED.second)
        } When
            {
              put(path)
            } Then
            {
              status(HttpStatus.BAD_REQUEST)
            } Extract
            {
              body().`as`(ErrorResponse::class.java)
            }
    errorResponse.errorSizeEquals(1)
    errorResponse.instanceEquals("/$path")
    errorResponse.timestampNotNull()
    errorResponse.errors[0].equalsTo(
        ErrorCode.UNSUPPORTED_FIELD_OPERATION, ErrorCode.UNSUPPORTED_FIELD_OPERATION.message)
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(UpdateField) should update name of mirror field`() {
    val path = "$COLLECTIONS_PATH/1/fields/13"
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON)
      body(
          WebContifyCollectionFieldApiUpdateRequest(
              "mirror_field_changed",
              "Mirror Field Changed",
              WebcontifyCollectionFieldType.RELATION_MIRROR,
              false))
    } When { put(path) } Then { status(HttpStatus.OK) }
  }
}

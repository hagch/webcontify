package io.webcontify.backend.collections.apis.item

import helpers.setups.api.ApiIntegrationTestSetup
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql

class DeleteItemApiIntegrationTest : ApiIntegrationTestSetup() {

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(DeleteItem) should delete item`() {
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
    } When { delete("$COLLECTIONS_PATH/1/items/1") } Then { status(HttpStatus.OK) }
  }

  @Test
  @Sql("/cleanup.sql", "./../collections-with-all-field-types.sql")
  fun `(DeleteItem) should not throw exception on item which does not exist`() {
    Given {
      mockMvc(mockMvc)
      contentType(MediaType.APPLICATION_JSON_VALUE)
    } When { delete("$COLLECTIONS_PATH/1/items/1001") } Then { status(HttpStatus.OK) }
  }

  // TODO add cases for identifiermap
}

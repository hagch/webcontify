package io.webcontify.backend.collections.services.field.handler.strategies

import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType
import org.jooq.impl.SQLDataType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextFieldHandlerTest {

  private val handler = TextFieldHandler()

  @Test
  fun `(getFieldType) should return type`() {
    assertEquals(SQLDataType.VARCHAR, handler.getFieldType())
  }

  @Test
  fun `(getFieldHandlerType) should return type`() {
    assertEquals(WebcontifyCollectionFieldType.TEXT, handler.getFieldHandlerType())
  }
}

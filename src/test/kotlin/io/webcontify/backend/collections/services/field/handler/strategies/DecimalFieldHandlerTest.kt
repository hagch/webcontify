package io.webcontify.backend.collections.services.field.handler.strategies

import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType
import org.jooq.impl.SQLDataType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DecimalFieldHandlerTest {

  private val handler = DecimalFieldHandler()

  @Test
  fun `(getFieldType) should return type`() {
    assertEquals(SQLDataType.DECIMAL, handler.getFieldType())
  }

  @Test
  fun `(getFieldHandlerType) should return type`() {
    assertEquals(WebcontifyCollectionFieldType.DECIMAL, handler.getFieldHandlerType())
  }
}

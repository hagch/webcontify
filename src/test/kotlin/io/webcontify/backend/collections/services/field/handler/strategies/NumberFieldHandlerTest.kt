package io.webcontify.backend.collections.services.field.handler.strategies

import org.jooq.impl.SQLDataType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NumberFieldHandlerTest {

  private val handler = NumberFieldHandler()

  @Test
  fun `(getFieldType) should return type`() {
    assertEquals(SQLDataType.BIGINT, handler.getFieldType())
  }
}

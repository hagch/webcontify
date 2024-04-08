package io.webcontify.backend.collections.services.field.handler.strategies

import org.jooq.impl.SQLDataType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TimestampFieldHandlerTest {

  private val handler = TimestampFieldHandler()

  @Test
  fun `(getFieldType) should return type`() {
    assertEquals(SQLDataType.LOCALDATETIME, handler.getFieldType())
  }
}

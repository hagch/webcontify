package io.webcontify.backend.collections.services.field.handler.strategies

import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType
import org.jooq.impl.SQLDataType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TimestampFieldHandlerTest {

  private val handler = TimestampFieldHandler()

  @Test
  fun `(getFieldType) should return type`() {
    assertEquals(SQLDataType.LOCALDATETIME, handler.getFieldType())
  }

  @Test
  fun `(getFieldHandlerType) should return type`() {
    assertEquals(WebcontifyCollectionFieldType.TIMESTAMP, handler.getFieldHandlerType())
  }
}

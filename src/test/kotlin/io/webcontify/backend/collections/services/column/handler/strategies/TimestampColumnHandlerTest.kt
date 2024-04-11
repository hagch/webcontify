package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.impl.SQLDataType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TimestampColumnHandlerTest {

  private val handler = TimestampColumnHandler()

  @Test
  fun `(getColumnType) should return type`() {
    assertEquals(SQLDataType.LOCALDATETIME, handler.getColumnType())
  }

  @Test
  fun `(getColumnHandlerType) should return type`() {
    assertEquals(WebcontifyCollectionColumnType.TIMESTAMP, handler.getColumnHandlerType())
  }
}

package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.impl.SQLDataType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TimestampColumnHandlerTest {

  private val handler = TimestampColumnHandler()

  @Test
  fun getColumnTypeShouldReturnType() {
    assertEquals(SQLDataType.TIMESTAMP, handler.getColumnType())
  }

  @Test
  fun getColumnHandlerTypeShouldReturnType() {
    assertEquals(WebcontifyCollectionColumnType.TIMESTAMP, handler.getColumnHandlerType())
  }
}

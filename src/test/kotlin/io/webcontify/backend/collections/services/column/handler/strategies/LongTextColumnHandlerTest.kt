package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.impl.SQLDataType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LongTextColumnHandlerTest {

  private val handler = LongTextColumnHandler()

  @Test
  fun getColumnTypeShouldReturnType() {
    assertEquals(SQLDataType.LONGVARCHAR, handler.getColumnType())
  }

  @Test
  fun getColumnHandlerTypeShouldReturnType() {
    assertEquals(WebcontifyCollectionColumnType.LONG_TEXT, handler.getColumnHandlerType())
  }
}

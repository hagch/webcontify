package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.impl.SQLDataType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShortTextColumnHandlerTest {

  private val handler = ShortTextColumnHandler()

  @Test
  fun getColumnTypeShouldReturnType() {
    assertEquals(SQLDataType.VARCHAR, handler.getColumnType())
  }

  @Test
  fun getColumnHandlerTypeShouldReturnType() {
    assertEquals(WebcontifyCollectionColumnType.SHORT_TEXT, handler.getColumnHandlerType())
  }
}

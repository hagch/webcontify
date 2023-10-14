package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.impl.SQLDataType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CurrencyColumnHandlerTest {

  private val handler = CurrencyColumnHandler()

  @Test
  fun getColumnTypeShouldReturnType() {
    assertEquals(SQLDataType.DECIMAL, handler.getColumnType())
  }

  @Test
  fun getColumnHandlerTypeShouldReturnType() {
    assertEquals(WebcontifyCollectionColumnType.CURRENCY, handler.getColumnHandlerType())
  }
}

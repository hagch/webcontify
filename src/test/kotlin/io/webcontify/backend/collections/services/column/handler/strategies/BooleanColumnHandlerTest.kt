package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.impl.SQLDataType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BooleanColumnHandlerTest {

  private val handler = BooleanColumnHandler()

  @Test
  fun getColumnTypeShouldReturnType() {
    assertEquals(SQLDataType.BOOLEAN, handler.getColumnType())
  }

  @Test
  fun getColumnHandlerTypeShouldReturnType() {
    assertEquals(WebcontifyCollectionColumnType.BOOLEAN, handler.getColumnHandlerType())
  }
}

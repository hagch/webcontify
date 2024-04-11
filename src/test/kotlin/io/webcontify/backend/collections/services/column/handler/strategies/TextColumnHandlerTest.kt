package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.impl.SQLDataType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextColumnHandlerTest {

  private val handler = TextColumnHandler()

  @Test
  fun `(getColumnType) should return type`() {
    assertEquals(SQLDataType.VARCHAR, handler.getColumnType())
  }

  @Test
  fun `(getColumnHandlerType) should return type`() {
    assertEquals(WebcontifyCollectionColumnType.TEXT, handler.getColumnHandlerType())
  }

}

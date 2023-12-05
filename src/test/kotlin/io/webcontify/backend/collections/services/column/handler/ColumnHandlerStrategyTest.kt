package io.webcontify.backend.collections.services.column.handler

import helpers.suppliers.firstSqlInsertedColumn
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.DataType
import org.jooq.impl.SQLDataType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ColumnHandlerStrategyTest {

  private val handler = ColumnHandlerTestImpl()

  private lateinit var strategy: ColumnHandlerStrategy

  @BeforeEach
  fun setup() {
    strategy = ColumnHandlerStrategy(listOf(handler))
  }

  @Test
  fun generateHandlerMapShouldRegisterHandlers() {
    strategy.generateHandlerMap()

    assertEquals(handler, strategy.getHandlerFor(firstSqlInsertedColumn()))
  }

  @Test
  fun getHandlerForShouldThrowUnprocessableContentExceptionOnNoHandlerFound() {
    assertThrows<UnprocessableContentException> { strategy.getHandlerFor(firstSqlInsertedColumn()) }
  }
}

private class ColumnHandlerTestImpl : ColumnHandler {
  override fun getColumnType(): DataType<*> {
    return SQLDataType.TIME
  }

  override fun getColumnHandlerType(): WebcontifyCollectionColumnType {
    return WebcontifyCollectionColumnType.NUMBER
  }

  override fun castToJavaType(value: Any): Any {
    return value
  }
}
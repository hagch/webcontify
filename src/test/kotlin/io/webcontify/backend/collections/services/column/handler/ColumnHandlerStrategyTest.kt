package io.webcontify.backend.collections.services.column.handler

import helpers.suppliers.firstSqlInsertedColumn
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnConfigurationDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnNumberConfigurationDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.DataType
import org.jooq.JSONB
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

private class ColumnHandlerTestImpl : ColumnHandler<Long> {
  override fun getColumnType(): DataType<Long> {
    return SQLDataType.BIGINT
  }

  override fun getColumnHandlerType(): WebcontifyCollectionColumnType {
    return WebcontifyCollectionColumnType.NUMBER
  }

  override fun castToJavaType(value: Any?): Long {
    return value.toString().toLong()
  }

  override fun mapJSONBToConfiguration(configuration: JSONB?): WebContifyCollectionColumnConfigurationDto<Long>? {
    TODO("Not yet implemented")
  }
}

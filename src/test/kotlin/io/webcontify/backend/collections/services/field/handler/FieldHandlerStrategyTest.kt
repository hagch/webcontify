package io.webcontify.backend.collections.services.field.handler

import helpers.suppliers.firstSqlInsertedField
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldConfigurationDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.SQLDataType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FieldHandlerStrategyTest {

  private val handler = FieldHandlerTestImpl()

  private lateinit var strategy: FieldHandlerStrategy

  @BeforeEach
  fun setup() {
    strategy = FieldHandlerStrategy(listOf(handler))
  }

  @Test
  fun `(generateHandlerMap) should register handlers`() {
    strategy.generateHandlerMap()

    assertEquals(handler, strategy.getHandlerFor(firstSqlInsertedField()))
  }

  @Test
  fun `(getHandlerFor) should throw unprocessable content exception on no handler found`() {
    assertThrows<UnprocessableContentException> { strategy.getHandlerFor(firstSqlInsertedField()) }
  }
}

private class FieldHandlerTestImpl : FieldHandler<Long> {
  override fun getFieldType(): DataType<Long> {
    return SQLDataType.BIGINT
  }

  override fun getFieldHandlerType(): WebcontifyCollectionFieldType {
    return WebcontifyCollectionFieldType.NUMBER
  }

  override fun castToJavaType(value: Any?): Long {
    return value.toString().toLong()
  }

  override fun mapJSONBToConfiguration(
      configuration: JSONB?
  ): WebContifyCollectionFieldConfigurationDto<Long>? {
    TODO("Not yet implemented")
  }
}

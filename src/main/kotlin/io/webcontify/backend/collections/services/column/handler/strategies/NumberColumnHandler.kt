package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.collections.models.dtos.CastException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnConfigurationDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnNumberConfigurationDto
import io.webcontify.backend.collections.services.column.handler.ColumnHandler
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.SQLDataType
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter
import org.springframework.stereotype.Service

@Service
class NumberColumnHandler : ColumnHandler {

  private val converter =
      JSONBtoJacksonConverter(WebContifyCollectionColumnNumberConfigurationDto::class.java)

  override fun mapJSONBToConfiguration(
      configuration: JSONB?
  ): WebContifyCollectionColumnConfigurationDto? {
    return converter.from(configuration)
  }

  override fun getColumnType(): DataType<Long> {
    return SQLDataType.BIGINT
  }

  override fun getColumnHandlerType(): WebcontifyCollectionColumnType {
    return WebcontifyCollectionColumnType.NUMBER
  }

  override fun castToJavaType(value: Any): Any {
    if (value is Long) {
      return value
    }
    if (value is Int) {
      return value
    }
    if (value is String) {
      return value.toLong()
    }
    throw CastException()
  }
}

package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.collections.models.dtos.CastException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnConfigurationDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnTextConfigurationDto
import io.webcontify.backend.collections.services.column.handler.ColumnHandler
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.SQLDataType
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter
import org.springframework.stereotype.Service

@Service
class TextColumnHandler : ColumnHandler {

  private val converter =
      JSONBtoJacksonConverter(WebContifyCollectionColumnTextConfigurationDto::class.java)

  override fun mapJSONBToConfiguration(
      configuration: JSONB?
  ): WebContifyCollectionColumnConfigurationDto? {
    return converter.from(configuration)
  }

  override fun getColumnType(): DataType<String> {
    return SQLDataType.VARCHAR
  }

  override fun getColumnHandlerType(): WebcontifyCollectionColumnType {
    return WebcontifyCollectionColumnType.TEXT
  }

  override fun castToJavaType(value: Any): Any {
    if (value is String) {
      return value
    }
    throw CastException()
  }
}

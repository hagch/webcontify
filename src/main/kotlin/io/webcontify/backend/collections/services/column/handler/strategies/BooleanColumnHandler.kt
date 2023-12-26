package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.collections.models.dtos.CastException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnBooleanConfigurationDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnConfigurationDto
import io.webcontify.backend.collections.services.column.handler.ColumnHandler
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.SQLDataType
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter
import org.springframework.stereotype.Service

@Service
class BooleanColumnHandler : ColumnHandler<Boolean> {

  private val converter =
      JSONBtoJacksonConverter(WebContifyCollectionColumnBooleanConfigurationDto::class.java)

  override fun getColumnType(): DataType<Boolean> {
    return SQLDataType.BOOLEAN
  }

  override fun getColumnHandlerType(): WebcontifyCollectionColumnType {
    return WebcontifyCollectionColumnType.BOOLEAN
  }

  override fun castToJavaType(value: Any?): Boolean? {
    if (value == null) {
      return null
    }
    if (value is Boolean) {
      return value
    }
    if (value is String) {
      return value.toBooleanStrictOrNull()
    }
    throw CastException()
  }

  override fun mapJSONBToConfiguration(
      configuration: JSONB?
  ): WebContifyCollectionColumnBooleanConfigurationDto? {
    return converter.from(configuration)
  }
}

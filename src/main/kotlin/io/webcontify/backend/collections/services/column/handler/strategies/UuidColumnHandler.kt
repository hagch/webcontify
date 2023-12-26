package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.collections.models.dtos.*
import io.webcontify.backend.collections.services.column.handler.ColumnHandler
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import java.util.*
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter
import org.springframework.stereotype.Service

@Service
class UuidColumnHandler : ColumnHandler<UUID> {

  private val converter =
      JSONBtoJacksonConverter(WebContifyCollectionColumnUuidConfigurationDto::class.java)

  override fun getColumnType(): DataType<UUID> {
    return SQLDataType.UUID
  }

  override fun getColumnType(column: WebContifyCollectionColumnDto): DataType<UUID> {
    var type = super.getColumnType(column)
    if (column.isPrimaryKey) {
      type = type.defaultValue(DSL.uuid())
    }
    return type
  }

  override fun getColumnHandlerType(): WebcontifyCollectionColumnType {
    return WebcontifyCollectionColumnType.UUID
  }

  override fun castToJavaType(value: Any?): UUID? {
    if (value == null) {
      return null
    }
    if (value is UUID) {
      return value
    }
    if (value is String) {
      try {
        return UUID.fromString(value)
      } catch (exception: IllegalArgumentException) {
        throw CastException()
      }
    }
    throw CastException()
  }

  override fun mapJSONBToConfiguration(
      configuration: JSONB?
  ): WebContifyCollectionColumnUuidConfigurationDto? {
    return converter.from(configuration)
  }
}

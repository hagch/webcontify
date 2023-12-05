package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.collections.models.dtos.CastException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnConfigurationDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnTimestampConfigurationDto
import io.webcontify.backend.collections.services.column.handler.ColumnHandler
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.SQLDataType
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter
import org.springframework.stereotype.Service

@Service
class TimestampColumnHandler : ColumnHandler {

  private val converter =
      JSONBtoJacksonConverter(WebContifyCollectionColumnTimestampConfigurationDto::class.java)

  override fun mapJSONBToConfiguration(
      configuration: JSONB?
  ): WebContifyCollectionColumnConfigurationDto? {
    return converter.from(configuration)
  }

  override fun getColumnType(): DataType<*> {
    return SQLDataType.TIMESTAMP
  }

  override fun getColumnHandlerType(): WebcontifyCollectionColumnType {
    return WebcontifyCollectionColumnType.TIMESTAMP
  }

  override fun castToJavaType(value: Any): Any {
    if (value is LocalDateTime) {
      return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value)
    }
    if (value is String) {
      return LocalDateTime.parse(value)
    }
    throw CastException()
  }
}

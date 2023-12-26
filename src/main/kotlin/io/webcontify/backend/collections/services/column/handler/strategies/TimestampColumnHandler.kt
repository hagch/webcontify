package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.collections.models.dtos.*
import io.webcontify.backend.collections.services.column.handler.ColumnHandler
import io.webcontify.backend.collections.utils.addGreaterThanIfPresent
import io.webcontify.backend.collections.utils.addLessThanIfPresent
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.jooq.ConstraintEnforcementStep
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.SQLDataType
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter
import org.springframework.stereotype.Service

@Service
class TimestampColumnHandler : ColumnHandler<LocalDateTime> {

  private val converter =
      JSONBtoJacksonConverter(WebContifyCollectionColumnTimestampConfigurationDto::class.java)
  private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  override fun mapJSONBToConfiguration(
      configuration: JSONB?
  ): WebContifyCollectionColumnConfigurationDto? {
    return converter.from(configuration)
  }

  override fun getColumnType(): DataType<LocalDateTime> {
    return SQLDataType.LOCALDATETIME
  }

  override fun getColumnHandlerType(): WebcontifyCollectionColumnType {
    return WebcontifyCollectionColumnType.TIMESTAMP
  }

  override fun getColumnConstraints(
      column: WebContifyCollectionColumnDto,
      tableName: String
  ): List<ConstraintEnforcementStep> {
    val list = super.getColumnConstraints(column, tableName).toMutableList()
    column.configuration?.let {
      if (it is WebContifyCollectionColumnTimestampConfigurationDto) {
        list.addLessThanIfPresent(
            tableName, column.name, it.lowerThan, castToJavaType(it.lowerThan))
        list.addGreaterThanIfPresent(
            tableName, column.name, it.greaterThan, castToJavaType(it.greaterThan))
      }
    }
    return list.toList()
  }

  override fun castToJavaType(value: Any?): LocalDateTime? {
    if (value is LocalDateTime) {
      return value
    }
    if (value is String) {
      return LocalDateTime.parse(value, formatter)
    }
    throw CastException()
  }
}

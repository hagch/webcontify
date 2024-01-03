package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.collections.models.dtos.*
import io.webcontify.backend.collections.services.column.handler.ColumnHandler
import io.webcontify.backend.collections.utils.addGreaterThanIfPresent
import io.webcontify.backend.collections.utils.addLessThanIfPresent
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.ConstraintEnforcementStep
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.SQLDataType
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter
import org.springframework.stereotype.Service

@Service
class NumberColumnHandler : ColumnHandler<Long> {

  private val converter =
      JSONBtoJacksonConverter(WebContifyCollectionColumnNumberConfigurationDto::class.java)

  override fun mapJSONBToConfiguration(
      configuration: JSONB?
  ): WebContifyCollectionColumnNumberConfigurationDto? {
    return converter.from(configuration)
  }

  override fun getColumnType(): DataType<Long> {
    return SQLDataType.BIGINT
  }

  override fun getColumnType(column: WebContifyCollectionColumnDto): DataType<Long> {
    var type = super.getColumnType(column)
    if (column.isPrimaryKey) {
      type = type.identity(true)
    }
    return type
  }

  override fun getColumnConstraints(
      column: WebContifyCollectionColumnDto,
      tableName: String
  ): List<ConstraintEnforcementStep> {
    val list = super.getColumnConstraints(column, tableName).toMutableList()
    column.configuration?.let {
      if (WebContifyCollectionColumnNumberConfigurationDto::class.isInstance(it)) {
        it as WebContifyCollectionColumnNumberConfigurationDto
        list.addLessThanIfPresent(tableName, column.name, it.lowerThan, it.lowerThan)
        list.addGreaterThanIfPresent(tableName, column.name, it.lowerThan, it.lowerThan)
      }
    }
    return list.toList()
  }

  override fun getColumnHandlerType(): WebcontifyCollectionColumnType {
    return WebcontifyCollectionColumnType.NUMBER
  }

  override fun castToJavaType(value: Any?): Long? {
    if (value == null) {
      return null
    }
    if (value is Long) {
      return value
    }
    if (value is Int) {
      return value.toLong()
    }
    if (value is String) {
      try {
        return value.toLong()
      } catch (exception: NumberFormatException) {
        throw CastException()
      }
    }
    throw CastException()
  }

  override fun validateColumn(
      value: Long?,
      configuration: WebContifyCollectionColumnConfigurationDto<Any>?
  ) {
    super.validateColumn(value, configuration)
    configuration?.let {
      it as WebContifyCollectionColumnNumberConfigurationDto
      if (value == null) {
        return
      }
      if (it.greaterThan != null && value <= it.greaterThan) {
        throw ValidationException()
      }
      if (it.lowerThan != null && value >= it.lowerThan) {
        throw ValidationException()
      }
    }
  }
}

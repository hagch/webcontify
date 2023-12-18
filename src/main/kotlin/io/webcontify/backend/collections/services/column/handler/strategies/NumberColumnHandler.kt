package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.collections.models.dtos.*
import io.webcontify.backend.collections.services.column.handler.ColumnHandler
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.ConstraintEnforcementStep
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter
import org.springframework.stereotype.Service

@Service
class NumberColumnHandler : ColumnHandler<Long> {

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
      if (it is WebContifyCollectionColumnNumberConfigurationDto) {
        if (it.lowerThan != null) {
          list.add(
              DSL.constraint("lt_${tableName}_${column.name}")
                  .check(DSL.field(column.name).lessThan(castToJavaType(it.lowerThan))))
        }
        if (it.greaterThan != null) {
          list.add(
              DSL.constraint("gt_${tableName}_${column.name}")
                  .check(DSL.field(column.name).greaterThan(castToJavaType(it.greaterThan))))
        }
      }
    }
    return list.toList()
  }

  override fun getColumnHandlerType(): WebcontifyCollectionColumnType {
    return WebcontifyCollectionColumnType.NUMBER
  }

  override fun castToJavaType(value: Any): Long {
    if (value is Long) {
      return value
    }
    if (value is Int) {
      return value.toLong()
    }
    if (value is String) {
      return value.toLong()
    }
    throw CastException()
  }
}

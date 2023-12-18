package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.collections.models.dtos.*
import io.webcontify.backend.collections.services.column.handler.ColumnHandler
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import java.math.BigDecimal
import org.jooq.ConstraintEnforcementStep
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter
import org.springframework.stereotype.Service

@Service
class DecimalColumnHandler : ColumnHandler<BigDecimal> {

  private val converter =
      JSONBtoJacksonConverter(WebContifyCollectionColumnDecimalConfigurationDto::class.java)

  override fun mapJSONBToConfiguration(
      configuration: JSONB?
  ): WebContifyCollectionColumnConfigurationDto? {
    return converter.from(configuration)
  }

  override fun getColumnType(column: WebContifyCollectionColumnDto): DataType<BigDecimal> {
    var type = super.getColumnType(column)
    column.configuration?.let {
      if (it is WebContifyCollectionColumnDecimalConfigurationDto) {
        if (it.precision != null) {
          type = type.precision(it.precision)
        }
        if (it.scale != null) {
          type = type.scale(it.scale)
        }
      }
    }
    return type
  }

  override fun getColumnType(): DataType<BigDecimal> {
    return SQLDataType.DECIMAL
  }

  override fun getColumnHandlerType(): WebcontifyCollectionColumnType {
    return WebcontifyCollectionColumnType.DECIMAL
  }

  override fun getColumnConstraints(
      column: WebContifyCollectionColumnDto,
      tableName: String
  ): List<ConstraintEnforcementStep> {
    val list = super.getColumnConstraints(column, tableName).toMutableList()
    column.configuration?.let {
      if (it is WebContifyCollectionColumnDecimalConfigurationDto) {
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

  override fun castToJavaType(value: Any): BigDecimal {
    if (value is Double) {
      return value.toBigDecimal()
    }
    if (value is String) {
      return value.toBigDecimal()
    }
    throw CastException()
  }
}

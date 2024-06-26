package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.collections.models.dtos.*
import io.webcontify.backend.collections.services.column.handler.ColumnHandler
import io.webcontify.backend.collections.utils.addGreaterThanIfPresent
import io.webcontify.backend.collections.utils.addLessThanIfPresent
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import java.math.BigDecimal
import org.jooq.ConstraintEnforcementStep
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.SQLDataType
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter
import org.springframework.stereotype.Service

@Service
class DecimalColumnHandler : ColumnHandler<BigDecimal> {

  private val converter =
      JSONBtoJacksonConverter(WebContifyCollectionColumnDecimalConfigurationDto::class.java)

  override fun mapJSONBToConfiguration(
      configuration: JSONB?
  ): WebContifyCollectionColumnDecimalConfigurationDto? {
    return converter.from(configuration)
  }

  override fun getColumnType(
      column: WebContifyCollectionColumnDto,
      isAutogenerated: Boolean
  ): DataType<BigDecimal> {
    var type = super.getColumnType(column, isAutogenerated)
    column.configuration?.let {
      it as WebContifyCollectionColumnDecimalConfigurationDto
      if (it.precision != null) {
        type = type.precision(it.precision)
      }
      if (it.scale != null) {
        type = type.scale(it.scale)
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
      if (WebContifyCollectionColumnDecimalConfigurationDto::class.isInstance(it)) {
        it as WebContifyCollectionColumnDecimalConfigurationDto
        list.addGreaterThanIfPresent(tableName, column.name, it.greaterThan, it.greaterThan)
        list.addLessThanIfPresent(tableName, column.name, it.lowerThan, it.lowerThan)
      }
    }
    return list.toList()
  }

  override fun castToJavaType(value: Any?): BigDecimal? {
    if (value is Double) {
      return value.toBigDecimal()
    }
    if (value is String) {
      try {
        return value.toBigDecimal()
      } catch (exception: NumberFormatException) {
        throw CastException()
      }
    }
    throw CastException()
  }

  override fun validateColumn(
      value: BigDecimal?,
      configuration: WebContifyCollectionColumnConfigurationDto<Any>?
  ) {
    super.validateColumn(value, configuration)
    configuration?.let {
      it as WebContifyCollectionColumnDecimalConfigurationDto
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

package io.webcontify.backend.collections.services.column.handler

import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.dtos.*
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import java.util.*
import org.jooq.ConstraintEnforcementStep
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.DSL.constraint
import org.jooq.impl.DSL.field
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter
import org.springframework.util.CollectionUtils

interface ColumnHandler<T> {
  fun getColumnType(): DataType<T>

  fun getColumnHandlerType(): WebcontifyCollectionColumnType

  fun getColumnType(column: WebContifyCollectionColumnDto): DataType<T> {
    var type = getColumnType()
    column.configuration?.let {
      if (column.isPrimaryKey) {
        type = type.nullable(false)
      } else {
        if (it.nullable != null) {
          type = type.nullable(it.nullable!!)
        }
        if (it.defaultValue != null) {
          type = type.defaultValue(it.defaultValue as T)
        }
      }
    }
    return type
  }

  @Throws(CastException::class) fun castToJavaType(value: Any?): T?

  fun getColumnConstraints(
      column: WebContifyCollectionColumnDto,
      tableName: String
  ): List<ConstraintEnforcementStep> {
    column.configuration?.let { configuration ->
      val constraints = mutableListOf<ConstraintEnforcementStep>()
      if (configuration.unique == true) {
        constraints.add(constraint("unique_${tableName}_${column.name}").unique(field(column.name)))
      }
      try {
        if (!configuration.inValues.isNullOrEmpty()) {
          constraints.add(
              constraint("in_values_${tableName}_${column.name}")
                  .check(field(column.name).`in`(configuration.inValues?.map { it })))
        }
      } catch (exception: CastException) {
        throw UnprocessableContentException(
            ErrorCode.INVALID_IN_VALUE_CONFIGURATION,
            configuration.inValues.toString(),
            column.name,
            column.type.name)
      }
      return constraints.toList()
    }
    return listOf()
  }

  fun mapConfigurationToJSONB(
      configuration: WebContifyCollectionColumnConfigurationDto<*>?
  ): JSONB? {
    return JSONBtoJacksonConverter(WebContifyCollectionColumnConfigurationDto::class.java)
        .to(configuration)
  }

  fun mapJSONBToConfiguration(configuration: JSONB?): WebContifyCollectionColumnConfigurationDto<T>?

  @Throws(ValidationException::class)
  fun validateColumn(value: T?, configuration: WebContifyCollectionColumnConfigurationDto<Any>?) {
    configuration?.let {
      if (it.nullable == false && Objects.isNull(value) && Objects.isNull(it.defaultValue)) {
        throw ValidationException()
      }
      if (!CollectionUtils.isEmpty(it.inValues) && it.inValues?.contains(value) == false) {
        throw ValidationException()
      }
    }
  }

  fun castAndValidate(
      value: Any?,
      configuration: WebContifyCollectionColumnConfigurationDto<Any>?
  ): T? {
    val castedValue = castToJavaType(value)
    validateColumn(castedValue, configuration)
    return castedValue
  }
}

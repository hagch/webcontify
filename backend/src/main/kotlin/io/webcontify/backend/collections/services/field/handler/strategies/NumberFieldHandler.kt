package io.webcontify.backend.collections.services.field.handler.strategies

import io.webcontify.backend.collections.models.dtos.*
import io.webcontify.backend.collections.services.field.handler.FieldHandler
import io.webcontify.backend.collections.services.field.handler.NUMBER_FIELD_TYPE
import io.webcontify.backend.collections.utils.addGreaterThanIfPresent
import io.webcontify.backend.collections.utils.addLessThanIfPresent
import org.jooq.ConstraintEnforcementStep
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.SQLDataType
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter
import org.springframework.stereotype.Component

@Component(NUMBER_FIELD_TYPE)
class NumberFieldHandler : FieldHandler<Long> {

  private val converter =
      JSONBtoJacksonConverter(WebContifyCollectionFieldNumberConfigurationDto::class.java)

  override fun mapJSONBToConfiguration(
      configuration: JSONB?
  ): WebContifyCollectionFieldNumberConfigurationDto? {
    return converter.from(configuration)
  }

  override fun getFieldType(): DataType<Long> {
    return SQLDataType.BIGINT
  }

  override fun getFieldType(
      field: WebContifyCollectionFieldDto,
      isAutogenerated: Boolean
  ): DataType<Long> {
    var type = super.getFieldType(field, isAutogenerated)!!
    if (field.isPrimaryKey && isAutogenerated) {
      type = type.identity(true)
    }
    return type
  }

  override fun getFieldConstraints(
      field: WebContifyCollectionFieldDto,
      tableName: String
  ): List<ConstraintEnforcementStep> {
    val list = super.getFieldConstraints(field, tableName).toMutableList()
    field.configuration?.let {
      if (WebContifyCollectionFieldNumberConfigurationDto::class.isInstance(it)) {
        it as WebContifyCollectionFieldNumberConfigurationDto
        list.addLessThanIfPresent(tableName, field.name, it.lowerThan, it.lowerThan)
        list.addGreaterThanIfPresent(tableName, field.name, it.lowerThan, it.lowerThan)
      }
    }
    return list.toList()
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

  override fun validateField(
      value: Long?,
      configuration: WebContifyCollectionFieldConfigurationDto<Any>?
  ): Long? {
    val validatedValue = super.validateField(value, configuration)
    configuration?.let {
      it as WebContifyCollectionFieldNumberConfigurationDto
      if (validatedValue == null) {
        return null
      }
      if (it.greaterThan != null && validatedValue <= it.greaterThan) {
        throw ValidationException()
      }
      if (it.lowerThan != null && validatedValue >= it.lowerThan) {
        throw ValidationException()
      }
    }
    return validatedValue
  }
}
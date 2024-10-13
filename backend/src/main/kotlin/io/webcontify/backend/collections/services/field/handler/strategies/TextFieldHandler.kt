package io.webcontify.backend.collections.services.field.handler.strategies

import io.webcontify.backend.collections.models.dtos.*
import io.webcontify.backend.collections.services.field.handler.FieldHandler
import io.webcontify.backend.collections.services.field.handler.TEXT_FIELD_TYPE
import org.jooq.ConstraintEnforcementStep
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter
import org.springframework.stereotype.Component

@Component(TEXT_FIELD_TYPE)
class TextFieldHandler : FieldHandler<String> {

  private val converter =
      JSONBtoJacksonConverter(WebContifyCollectionFieldTextConfigurationDto::class.java)

  override fun mapJSONBToConfiguration(
      configuration: JSONB?
  ): WebContifyCollectionFieldTextConfigurationDto? {
    return converter.from(configuration)
  }

  override fun getFieldType(): DataType<String> {
    return SQLDataType.VARCHAR
  }

  override fun getFieldConstraints(
      field: WebContifyCollectionFieldDto,
      tableName: String
  ): List<ConstraintEnforcementStep> {
    val list = super.getFieldConstraints(field, tableName).toMutableList()
    field.configuration?.let {
      it as WebContifyCollectionFieldTextConfigurationDto
      val dslField = DSL.field(field.name, SQLDataType.VARCHAR)
      if (it.minLength != null) {
        list.add(
            DSL.constraint("min_length_${tableName}_${dslField.name}")
                .check(DSL.length(dslField).greaterOrEqual(it.minLength)))
      }
      if (it.maxLength != null) {
        list.add(
            DSL.constraint("max_length_${tableName}_${dslField.name}")
                .check(DSL.length(dslField).lessOrEqual(it.maxLength)))
      }
      if (it.regex != null) {
        list.add(
            DSL.constraint("regex_${tableName}_${dslField.name}")
                .check(dslField.likeRegex(it.regex)))
      }
    }
    return list.toList()
  }

  override fun castToJavaType(value: Any?): String? {
    if (value == null) {
      return null
    }
    if (value is String) {
      return value
    }
    throw CastException()
  }

  override fun validateField(
      value: String?,
      configuration: WebContifyCollectionFieldConfigurationDto<Any>?
  ): String? {
    val validatedValue = super.validateField(value, configuration)
    configuration?.let {
      it as WebContifyCollectionFieldTextConfigurationDto
      if (validatedValue == null) {
        return null
      }
      if (it.minLength != null && validatedValue.length < it.minLength) {
        throw ValidationException()
      }
      if (it.maxLength != null && validatedValue.length > it.maxLength) {
        throw ValidationException()
      }
      if (it.regex != null && it.regex.toRegex().matchEntire(validatedValue) == null) {
        throw ValidationException()
      }
    }
    return validatedValue
  }
}

package io.webcontify.backend.collections.services.field.handler.strategies

import io.webcontify.backend.collections.models.dtos.*
import io.webcontify.backend.collections.services.field.handler.FieldHandler
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType
import org.jooq.ConstraintEnforcementStep
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter
import org.springframework.stereotype.Service

@Service
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

  override fun getFieldHandlerType(): WebcontifyCollectionFieldType {
    return WebcontifyCollectionFieldType.TEXT
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
  ) {
    super.validateField(value, configuration)
    configuration?.let {
      it as WebContifyCollectionFieldTextConfigurationDto
      if (value == null) {
        return
      }
      if (it.minLength != null && value.length < it.minLength) {
        throw ValidationException()
      }
      if (it.maxLength != null && value.length > it.maxLength) {
        throw ValidationException()
      }
    }
  }
}

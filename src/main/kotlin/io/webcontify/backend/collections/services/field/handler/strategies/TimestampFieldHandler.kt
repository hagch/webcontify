package io.webcontify.backend.collections.services.field.handler.strategies

import io.webcontify.backend.collections.models.dtos.*
import io.webcontify.backend.collections.services.field.handler.FieldHandler
import io.webcontify.backend.collections.utils.addGreaterThanIfPresent
import io.webcontify.backend.collections.utils.addLessThanIfPresent
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import org.jooq.ConstraintEnforcementStep
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.SQLDataType
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter
import org.springframework.stereotype.Service

@Service
class TimestampFieldHandler : FieldHandler<LocalDateTime> {

  private val converter =
      JSONBtoJacksonConverter(WebContifyCollectionFieldTimestampConfigurationDto::class.java)
  private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  override fun mapJSONBToConfiguration(
      configuration: JSONB?
  ): WebContifyCollectionFieldTimestampConfigurationDto? {
    return converter.from(configuration)
  }

  override fun getFieldType(): DataType<LocalDateTime> {
    return SQLDataType.LOCALDATETIME
  }

  override fun getFieldHandlerType(): WebcontifyCollectionFieldType {
    return WebcontifyCollectionFieldType.TIMESTAMP
  }

  override fun getFieldConstraints(
      field: WebContifyCollectionFieldDto,
      tableName: String
  ): List<ConstraintEnforcementStep> {
    val list = super.getFieldConstraints(field, tableName).toMutableList()
    field.configuration?.let {
      it as WebContifyCollectionFieldTimestampConfigurationDto
      list.addLessThanIfPresent(tableName, field.name, it.lowerThan, it.lowerThan)
      list.addGreaterThanIfPresent(tableName, field.name, it.greaterThan, it.greaterThan)
    }
    return list.toList()
  }

  override fun castToJavaType(value: Any?): LocalDateTime? {
    if (value == null) {
      return null
    }
    if (value is LocalDateTime) {
      return value
    }
    if (value is String) {
      try {
        return LocalDateTime.parse(value, formatter)
      } catch (exception: DateTimeParseException) {
        throw CastException()
      }
    }
    throw CastException()
  }

  override fun validateField(
      value: LocalDateTime?,
      configuration: WebContifyCollectionFieldConfigurationDto<Any>?
  ) {
    super.validateField(value, configuration)
    configuration?.let {
      it as WebContifyCollectionFieldTimestampConfigurationDto
      if (value == null) {
        return
      }
      if (it.lowerThan != null && value.isAfter(it.lowerThan)) {
        throw ValidationException()
      }
      if (it.greaterThan != null && value.isBefore(it.greaterThan)) {
        throw ValidationException()
      }
    }
  }
}

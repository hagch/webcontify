package io.webcontify.backend.collections.services.field.handler.strategies

import io.webcontify.backend.collections.models.dtos.*
import io.webcontify.backend.collections.services.field.handler.FieldHandler
import io.webcontify.backend.collections.services.field.handler.TIMESTAMP_FIELD_TYPE
import io.webcontify.backend.collections.utils.addGreaterThanIfPresent
import io.webcontify.backend.collections.utils.addLessThanIfPresent
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Objects
import org.jooq.ConstraintEnforcementStep
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.SQLDataType
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter
import org.springframework.stereotype.Component

@Component(TIMESTAMP_FIELD_TYPE)
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
    if (value is Timestamp) {
      return value.toLocalDateTime()
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
  ): LocalDateTime? {
    val validatedValue = super.validateField(value, configuration)
    configuration?.let {
      it as WebContifyCollectionFieldTimestampConfigurationDto
      if (validatedValue == null) {
        return null
      }
      if (it.lowerThan != null &&
          (validatedValue.isAfter(it.lowerThan) || Objects.equals(it.lowerThan, validatedValue))) {
        throw ValidationException()
      }
      if (it.greaterThan != null &&
          (validatedValue.isBefore(it.greaterThan) ||
              Objects.equals(it.greaterThan, validatedValue))) {
        throw ValidationException()
      }
    }
    return validatedValue
  }
}

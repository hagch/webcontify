package io.webcontify.backend.collections.services.field.handler.strategies

import io.webcontify.backend.collections.models.dtos.CastException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldBooleanConfigurationDto
import io.webcontify.backend.collections.services.field.handler.BOOLEAN_FIELD_TYPE
import io.webcontify.backend.collections.services.field.handler.FieldHandler
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.SQLDataType
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter
import org.springframework.stereotype.Component

@Component(BOOLEAN_FIELD_TYPE)
class BooleanFieldHandler : FieldHandler<Boolean> {

  private val converter =
      JSONBtoJacksonConverter(WebContifyCollectionFieldBooleanConfigurationDto::class.java)

  override fun getFieldType(): DataType<Boolean> {
    return SQLDataType.BOOLEAN
  }

  override fun castToJavaType(value: Any?): Boolean? {
    if (value == null) {
      return null
    }
    if (value is Boolean) {
      return value
    }
    if (value is String) {
      return value.toBooleanStrictOrNull()
    }
    throw CastException()
  }

  override fun mapJSONBToConfiguration(
      configuration: JSONB?
  ): WebContifyCollectionFieldBooleanConfigurationDto? {
    return converter.from(configuration)
  }
}

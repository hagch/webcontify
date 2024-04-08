package io.webcontify.backend.collections.services.field.handler

import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.dtos.CastException
import io.webcontify.backend.collections.models.dtos.ValidationException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldConfigurationDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldDto
import io.webcontify.backend.collections.models.errors.ErrorCode
import org.jooq.JSONB
import org.springframework.stereotype.Service

// TODO SPLIT INTO PARTS ONE FOR columns and one for items
@Service
class FieldHandlerStrategy(private val handlers: Map<String, FieldHandler<*>>) {

  fun getHandlerFor(field: WebContifyCollectionFieldDto): FieldHandler<*> {
    try {
      return handlers.getValue(field.type.name)
    } catch (e: NoSuchElementException) {
      throw UnprocessableContentException(
          ErrorCode.NO_HANDLER_FOR_FIELD_TYPE, field.name, field.type.name)
    }
  }

  fun castItemToJavaTypes(
      fields: List<WebContifyCollectionFieldDto>?,
      item: Map<String, Any?>
  ): Map<String, Any?> {
    return item.mapValues { entry ->
      val field = getFirstMatchingFieldFor(entry.key, fields)
      return@mapValues mapEntry(entry, field)
    }
  }

  private fun mapEntry(entry: Map.Entry<String, Any?>, field: WebContifyCollectionFieldDto) =
      (entry.value.let {
        try {
          return@let getHandlerFor(field).castAndValidate(it, field.configuration)
        } catch (exception: CastException) {
          throw UnprocessableContentException(
              ErrorCode.CAN_NOT_CAST_VALUE, entry.value.toString(), entry.key)
        } catch (exception: ValidationException) {
          throw UnprocessableContentException(
              ErrorCode.INVALID_VALUE_PASSED,
              entry.value.toString(),
              entry.key,
              field.configuration.toString())
        }
      }
          ?: entry.value)

  private fun getFirstMatchingFieldFor(
      key: String,
      fields: List<WebContifyCollectionFieldDto>?,
  ) =
      try {
        fields?.first { it.name.lowercase() == key.lowercase() }
      } catch (e: NoSuchElementException) {
        throw UnprocessableContentException(ErrorCode.UNDEFINED_FIELD, key)
      } ?: throw UnprocessableContentException(ErrorCode.UNDEFINED_FIELD, key)

  fun mapConfigurationToJSONB(field: WebContifyCollectionFieldDto): JSONB? {
    return getHandlerFor(field).mapConfigurationToJSONB(field.configuration)
  }

  fun mapJSONBToConfiguration(
      field: WebContifyCollectionFieldDto,
      configuration: JSONB?
  ): WebContifyCollectionFieldConfigurationDto<Any?>? {
    return getHandlerFor(field).mapJSONBToConfiguration(configuration)
  }
}

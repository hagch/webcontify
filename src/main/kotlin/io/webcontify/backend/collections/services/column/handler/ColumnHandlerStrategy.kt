package io.webcontify.backend.collections.services.column.handler

import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.dtos.*
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import jakarta.annotation.PostConstruct
import org.jooq.JSONB
import org.springframework.stereotype.Service

@Service
class ColumnHandlerStrategy(private val handlers: List<ColumnHandler<*>>) {

  private val handlerMap: MutableMap<WebcontifyCollectionColumnType, ColumnHandler<*>> =
      mutableMapOf()

  fun getHandlerFor(column: WebContifyCollectionColumnDto): ColumnHandler<*> {
    try {
      return handlerMap.getValue(column.type)
    } catch (e: NoSuchElementException) {
      throw UnprocessableContentException(
          ErrorCode.NO_HANDLER_FOR_COLUMN_TYPE, column.name, column.type.name)
    }
  }

  fun castItemToJavaTypes(
      columns: List<WebContifyCollectionColumnDto>?,
      item: Map<String, Any?>
  ): Map<String, Any?> {
    return item.mapValues { entry ->
      val column = getFirstMatchingColumnFor(entry.key, columns)
      return@mapValues mapEntry(entry, column)
    }
  }

  private fun mapEntry(entry: Map.Entry<String, Any?>, column: WebContifyCollectionColumnDto) =
      (entry.value.let {
        try {
          return@let getHandlerFor(column).castAndValidate(it, column.configuration)
        } catch (exception: CastException) {
          throw UnprocessableContentException(
              ErrorCode.CAN_NOT_CAST_VALUE, entry.value.toString(), entry.key)
        } catch (exception: ValidationException) {
          throw UnprocessableContentException(
              ErrorCode.INVALID_VALUE_PASSED,
              entry.value.toString(),
              entry.key,
              column.configuration.toString())
        }
      }
          ?: entry.value)

  private fun getFirstMatchingColumnFor(
      key: String,
      columns: List<WebContifyCollectionColumnDto>?,
  ) =
      try {
        columns?.first { it.name.lowercase() == key.lowercase() }
      } catch (e: NoSuchElementException) {
        throw UnprocessableContentException(ErrorCode.UNDEFINED_COLUMN, key)
      } ?: throw UnprocessableContentException(ErrorCode.UNDEFINED_COLUMN, key)

  @PostConstruct
  fun generateHandlerMap() {
    handlers.forEach { handlerMap[it.getColumnHandlerType()] = it }
  }

  fun mapConfigurationToJSONB(column: WebContifyCollectionColumnDto): JSONB? {
    return getHandlerFor(column).mapConfigurationToJSONB(column.configuration)
  }

  fun mapJSONBToConfiguration(
      column: WebContifyCollectionColumnDto,
      configuration: JSONB?
  ): WebContifyCollectionColumnConfigurationDto<Any?>? {
    return getHandlerFor(column).mapJSONBToConfiguration(configuration)
  }
}

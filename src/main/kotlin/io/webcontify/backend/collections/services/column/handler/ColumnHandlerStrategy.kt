package io.webcontify.backend.collections.services.column.handler

import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.utils.snakeToCamelCase
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service

@Service
class ColumnHandlerStrategy(private val handlers: List<ColumnHandler>) {

  private val handlerMap: MutableMap<WebcontifyCollectionColumnType, ColumnHandler> = mutableMapOf()

  fun getHandlerFor(type: WebcontifyCollectionColumnType): ColumnHandler {
    try {
      return handlerMap.getValue(type)
    } catch (e: NoSuchElementException) {
      throw UnprocessableContentException()
    }
  }

  fun castItemToJavaTypes(
      columns: List<WebContifyCollectionColumnDto>?,
      item: Map<String, Any?>
  ): Map<String, Any?> {
    return item.mapValues { entry ->
      val column =
          columns?.first { it.name.snakeToCamelCase().lowercase() == entry.key.lowercase() }
              ?: throw UnprocessableContentException()
      return@mapValues entry.value?.let {
        return@let getHandlerFor(column.type).castToJavaType(it)
      }
          ?: entry.value
    }
  }

  @PostConstruct
  fun generateHandlerMap() {
    handlers.forEach { handlerMap[it.getColumnHandlerType()] = it }
  }
}

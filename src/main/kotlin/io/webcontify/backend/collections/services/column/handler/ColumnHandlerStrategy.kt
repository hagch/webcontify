package io.webcontify.backend.collections.services.column.handler

import io.webcontify.backend.collections.exceptions.UnprocessableContentException
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

  @PostConstruct
  fun generateHandlerMap() {
    handlers.forEach { handlerMap[it.getColumnHandlerType()] = it }
  }
}

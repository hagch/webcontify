package io.webcontify.backend.collections.services.column.handler

import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

@Component
class ColumnHandlerStrategy(private val handlers: List<ColumnHandler>) {

  private val handlerMap: MutableMap<WebcontifyCollectionColumnType, ColumnHandler> = mutableMapOf()

  fun getHandlerFor(type: WebcontifyCollectionColumnType): ColumnHandler {
    return handlerMap.getValue(type)
  }

  @PostConstruct
  private fun generateHandlerMap() {
    handlers.forEach { handlerMap[it.getColumnHandlerType()] = it }
  }
}

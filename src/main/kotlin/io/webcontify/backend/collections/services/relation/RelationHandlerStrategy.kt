package io.webcontify.backend.collections.services.relation

import io.webcontify.backend.collections.models.dtos.*
import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service

@Service
class RelationHandlerStrategy(private val handlers: List<RelationHandler>) {

  private val handlerMap: MutableMap<WebcontifyCollectionRelationType, RelationHandler> =
      mutableMapOf()

  @PostConstruct
  private fun generateHandlerMap() {
    handlers.forEach { handlerMap[it.getType()] = it }
  }

  private fun getHandlerFor(relationType: WebcontifyCollectionRelationType): RelationHandler {
    try {
      return handlerMap.getValue(relationType)
    } catch (e: NoSuchElementException) {
      throw RuntimeException("TODO")
    }
  }

  fun createRelation(relation: Set<WebContifyCollectionRelationDto>) {
    getHandlerFor(relation.first().type).createRelation(relation)
  }
}

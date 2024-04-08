package io.webcontify.backend.relations.handler

import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType
import io.webcontify.backend.relations.CreateRelationDto
import io.webcontify.backend.relations.RelationCollectionDto
import io.webcontify.backend.relations.RelationDto
import org.springframework.stereotype.Component

@Component
class RelationFactory(val handlers: Map<String, RelationHandler>) {

  private fun getBy(relationType: WebcontifyCollectionRelationType): RelationHandler {
    return handlers[relationType.name] ?: throw RuntimeException("NOT EXISTENT")
  }

  fun create(crateRelationDto: CreateRelationDto): RelationDto {
    val handler = getBy(crateRelationDto.type)
    val relation = handler.saveRelation(crateRelationDto)
    handler.createTableRelation(relation)
    return relation
  }

  fun delete(relationCollectionDto: RelationCollectionDto) {
    val handler = getBy(relationCollectionDto.type)
    handler.deleteRelation(relationCollectionDto)
  }
}

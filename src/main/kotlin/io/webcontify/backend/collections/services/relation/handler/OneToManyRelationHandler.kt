package io.webcontify.backend.collections.services.relation.handler

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationDto
import io.webcontify.backend.collections.repositories.CollectionRelationRepository
import io.webcontify.backend.collections.repositories.CollectionTableRelationRepository
import io.webcontify.backend.collections.services.relation.RelationHandler
import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType
import org.springframework.stereotype.Service

@Service
class OneToManyRelationHandler(
    val tableRelationRepository: CollectionTableRelationRepository,
    val relationRepository: CollectionRelationRepository
) : RelationHandler {
  override fun getType(): WebcontifyCollectionRelationType {
    return WebcontifyCollectionRelationType.ONE_TO_MANY
  }

  override fun createRelation(
      relation: WebContifyCollectionRelationDto
  ): WebContifyCollectionRelationDto {
    relationRepository.create(relation)
    val oneToManyRelation = relation.switchReference(WebcontifyCollectionRelationType.MANY_TO_ONE)
    tableRelationRepository.create(oneToManyRelation)
    relationRepository.create(oneToManyRelation)
    return relation
  }
}

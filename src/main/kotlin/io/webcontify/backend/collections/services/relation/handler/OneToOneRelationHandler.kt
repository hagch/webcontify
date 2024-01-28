package io.webcontify.backend.collections.services.relation.handler

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationDto
import io.webcontify.backend.collections.repositories.CollectionRelationRepository
import io.webcontify.backend.collections.repositories.CollectionTableRelationRepository
import io.webcontify.backend.collections.services.relation.RelationHandler
import io.webcontify.backend.collections.utils.switchReferences
import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType
import org.springframework.stereotype.Service

@Service
class OneToOneRelationHandler(
    val tableRelationRepository: CollectionTableRelationRepository,
    val relationRepository: CollectionRelationRepository
) : RelationHandler {
  override fun getType(): WebcontifyCollectionRelationType {
    return WebcontifyCollectionRelationType.ONE_TO_ONE
  }

  override fun createRelation(relation: Set<WebContifyCollectionRelationDto>) {
    tableRelationRepository.create(relation)
    relationRepository.create(relation)
    relationRepository.create(relation.switchReferences(WebcontifyCollectionRelationType.ONE_TO_ONE))
  }
}

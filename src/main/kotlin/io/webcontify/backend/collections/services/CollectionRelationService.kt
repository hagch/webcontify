package io.webcontify.backend.collections.services

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationDto
import io.webcontify.backend.collections.repositories.CollectionRelationRepository
import io.webcontify.backend.collections.services.relation.RelationHandlerStrategy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CollectionRelationService(
    val relationHandlerStrategy: RelationHandlerStrategy,
    val relationRepository: CollectionRelationRepository
) {

  @Transactional
  fun delete(sourceCollectionId: Int, name: String) {
    // TODO handle delete in tableRelationRepository
    return relationRepository.delete(sourceCollectionId, name)
  }

  @Transactional
  fun create(relation: WebContifyCollectionRelationDto): WebContifyCollectionRelationDto {
    return relationHandlerStrategy.createRelation(relation)
  }
}

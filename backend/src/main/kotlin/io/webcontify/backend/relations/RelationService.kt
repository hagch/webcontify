package io.webcontify.backend.relations

import io.webcontify.backend.relations.handler.RelationFactory
import org.springframework.stereotype.Service

@Service
class RelationService(
    private val factory: RelationFactory,
    private val relationRepository: RelationRepository
) {

  fun create(createRelationDto: CreateRelationDto): RelationDto {
    return factory.create(createRelationDto)
  }

  fun delete(relationId: Long) {
    val relation = relationRepository.findById(relationId)
    factory.delete(relation)
    relationRepository.delete(relationId)
  }

  fun isUsedAsMappingTable(collectionId: Long): Boolean {
    return relationRepository.isCollectionUsedAsMappingTableInRelation(collectionId)
  }
}

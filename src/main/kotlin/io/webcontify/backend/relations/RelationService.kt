package io.webcontify.backend.relations

import io.webcontify.backend.relations.handler.RelationFactory
import org.springframework.stereotype.Service

@Service
class RelationService(private val factory: RelationFactory) {

  fun create(createRelationDto: CreateRelationDto): RelationDto {
    return factory.create(createRelationDto)
  }
}

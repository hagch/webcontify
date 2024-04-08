package io.webcontify.backend.relations.handler

import io.webcontify.backend.relations.CreateRelationDto
import io.webcontify.backend.relations.RelationCollectionDto
import io.webcontify.backend.relations.RelationDto

interface RelationHandler {

  fun createTableRelation(relation: RelationDto)

  fun saveRelation(relation: CreateRelationDto): RelationDto

  fun deleteRelation(relation: RelationCollectionDto)
}

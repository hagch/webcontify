package io.webcontify.backend.relations.handlers

import io.webcontify.backend.relations.models.CreateRelationDto
import io.webcontify.backend.relations.models.RelationCollectionDto
import io.webcontify.backend.relations.models.RelationDto

interface RelationHandler {

  fun createTableRelation(relation: RelationDto)

  fun saveRelation(relation: CreateRelationDto): RelationDto

  fun deleteRelation(relation: RelationCollectionDto)
}

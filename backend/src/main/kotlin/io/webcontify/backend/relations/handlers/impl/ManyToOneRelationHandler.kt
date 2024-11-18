package io.webcontify.backend.relations.handlers.impl

import io.webcontify.backend.relations.*
import io.webcontify.backend.relations.handlers.RelationHandler
import io.webcontify.backend.relations.mappers.RelationMapper
import io.webcontify.backend.relations.models.CreateRelationDto
import io.webcontify.backend.relations.models.RelationCollectionDto
import io.webcontify.backend.relations.models.RelationDto
import io.webcontify.backend.relations.models.TableRelationDto
import org.springframework.stereotype.Component

@Component(MANY_TO_ONE)
class ManyToOneRelationHandler(
    private val relationMapper: RelationMapper,
    private val tableRelationRepository: TableRelationRepository,
    private val relationRepository: RelationRepository
) : RelationHandler {
  override fun saveRelation(relation: CreateRelationDto): RelationDto {
    if (relation.mappingCollectionMapping != null)
        throw RuntimeException("No mapping collection allowed")
    val relationDto = relationRepository.create(relation)
    return relationDto
  }

  override fun createTableRelation(relation: RelationDto) {
    val sourceTableRelation =
        relation.sourceCollectionMapping.let {
          relationMapper.toTableRelationDto(
              it.id!!, it.fieldsMapping.map { field -> field.sourceFieldId }.toSet())
        }
    val referencedTableRelation =
        relation.referencedCollectionMapping.let {
          relationMapper.toTableRelationDto(
              it.id!!, it.fieldsMapping.map { field -> field.sourceFieldId }.toSet())
        }
    tableRelationRepository.create(
        TableRelationDto(
            relationId = relation.id,
            sourceTable = sourceTableRelation,
            referencedTable = referencedTableRelation))
  }

  override fun deleteRelation(relation: RelationCollectionDto) {
    tableRelationRepository.delete(relation)
  }
}

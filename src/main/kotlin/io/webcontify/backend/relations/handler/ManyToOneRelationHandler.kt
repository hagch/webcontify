package io.webcontify.backend.relations.handler

import io.webcontify.backend.relations.*
import org.springframework.stereotype.Component

@Component(MANY_TO_ONE)
class ManyToOneRelationHandler(
    private val relationMapper: RelationMapper,
    private val tableRelationRepository: TableRelationRepository,
    private val relationRepository: RelationRepository,
    private val mirrorFieldService: MirrorFieldService
) : RelationHandler {
  override fun saveRelation(relation: CreateRelationDto): RelationDto {
    if (relation.mappingCollectionMapping != null)
        throw RuntimeException("No mapping collection allowed")
    mirrorFieldService.mustBeEmpty(relation.sourceCollectionMapping.mirrorFields)
    mirrorFieldService.canBeEmpty(
        relation.referencedCollectionMapping.mirrorFields,
        relation.sourceCollectionMapping.fieldsMapping)
    val relationDto = relationRepository.create(relation)
    val mirrorFields =
        mirrorFieldService.create(relation.referencedCollectionMapping, relationDto.id)
    relationDto.mirrorFields = mirrorFields
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

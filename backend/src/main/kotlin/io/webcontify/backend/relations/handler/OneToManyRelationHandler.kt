package io.webcontify.backend.relations.handler

import io.webcontify.backend.relations.*
import org.springframework.stereotype.Component

@Component(ONE_TO_MANY)
class OneToManyRelationHandler(
    private val relationMapper: RelationMapper,
    private val tableRelationRepository: TableRelationRepository,
    private val relationRepository: RelationRepository,
    private val mirrorFieldService: MirrorFieldService
) : RelationHandler {
  override fun saveRelation(relation: CreateRelationDto): RelationDto {
    if (relation.mappingCollectionMapping != null)
        throw RuntimeException("No mapping collection allowed")
    mirrorFieldService.mustBeEmpty(relation.referencedCollectionMapping.mirrorFields)
    mirrorFieldService.canBeEmpty(
        relation.sourceCollectionMapping.mirrorFields,
        relation.referencedCollectionMapping.fieldsMapping)
    val relationDto = relationRepository.create(relation)
    val mirrorFields =
        mirrorFieldService.create(
            relation.sourceCollectionMapping,
            relationDto.id,
            relation.referencedCollectionMapping.id)
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
            sourceTable = referencedTableRelation,
            referencedTable = sourceTableRelation))
  }

  override fun deleteRelation(relation: RelationCollectionDto) {
    val relationDto =
        relation.copy(
            sourceCollection = relation.referencedCollection,
            referencedCollection = relation.sourceCollection)
    tableRelationRepository.delete(relationDto)
  }
}

package io.webcontify.backend.relations.handler

import io.webcontify.backend.relations.*
import org.springframework.stereotype.Component

@Component
class OneToOneRelationHandler(
    private val relationMapper: RelationMapper,
    private val tableRelationRepository: TableRelationRepository,
    private val relationRepository: RelationRepository
) : RelationHandler {
  override fun saveRelation(relation: CreateRelationDto): RelationDto {
    if (relation.mappingCollectionMapping == null)
        throw RuntimeException("No mapping collection allowed")
    return relationRepository.create(relation)
  }

  override fun createTableRelation(relation: RelationDto) {
    val sourceTableRelation =
        relation.sourceCollectionMapping.let {
          relationMapper.toTableRelationDto(
              it.id, it.fieldsMapping.map { field -> field.sourceFieldId }.toSet())
        }
    val referencedTableRelation =
        relation.referencedCollectionMapping.let {
          relationMapper.toTableRelationDto(
              it.id, it.fieldsMapping.map { field -> field.sourceFieldId }.toSet())
        }
    tableRelationRepository.create(
        TableRelationDto(
            relationId = relation.id,
            sourceTable = sourceTableRelation,
            referencedTable = referencedTableRelation))
  }
}

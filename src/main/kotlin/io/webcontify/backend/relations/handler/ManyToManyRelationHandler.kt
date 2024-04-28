package io.webcontify.backend.relations.handler

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.repositories.CollectionRepository
import io.webcontify.backend.collections.repositories.CollectionTableRepository
import io.webcontify.backend.relations.*
import org.springframework.stereotype.Component

@Component
class ManyToManyRelationHandler(
    private val relationMapper: RelationMapper,
    private val tableRelationRepository: TableRelationRepository,
    private val collectionRepository: CollectionRepository,
    private val tableRepository: CollectionTableRepository,
    private val relationRepository: RelationRepository
) : RelationHandler {
  override fun saveRelation(relation: CreateRelationDto): RelationDto {
    val mappingCollectionMapping =
        relation.mappingCollectionMapping ?: createMappingCollection(relation)
    return relationRepository.create(
        relation.copy(mappingCollectionMapping = mappingCollectionMapping))
  }

  override fun createTableRelation(relation: RelationDto) {
    val mappingCollection =
        relation.mappingCollectionMapping ?: throw RuntimeException("cannot be null")
    val mappingTableRelation =
        mappingCollection.let {
          relationMapper.toTableRelationDto(
              it.id, it.fieldsMapping.map { field -> field.sourceFieldId }.toSet())
        }
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
            sourceTable = mappingTableRelation,
            referencedTable = referencedTableRelation))
    tableRelationRepository.create(
        TableRelationDto(
            relationId = relation.id,
            sourceTable = mappingTableRelation,
            referencedTable = sourceTableRelation))
  }

  private fun createMappingCollection(relation: CreateRelationDto): CollectionRelationMapping {
    val sourceCollectionFields =
        relation.sourceCollectionMapping.fieldsMapping.map { it.sourceFieldId }.toSet()
    val sourceCollection =
        relationMapper.toTableRelationDto(
            relation.sourceCollectionMapping.id, sourceCollectionFields)
    val referencedCollectionFields =
        relation.referencedCollectionMapping.fieldsMapping.map { it.referencedFieldId }.toSet()
    val referencedCollection =
        relationMapper.toTableRelationDto(
            relation.referencedCollectionMapping.id, referencedCollectionFields)
    val sourceFieldMapping =
        sourceCollection.fields.map {
          val field = it.copy(name = "ref_${it.name}", displayName = "Reference ${it.displayName}")
          Pair(field, Pair(field.name, it.id))
        }
    val referencedFieldMapping =
        referencedCollection.fields.map {
          val field = it.copy(name = "ref_${it.name}", displayName = "Reference ${it.displayName}")
          Pair(field, Pair(field.name, it.id))
        }
    val fieldMappings = sourceFieldMapping + referencedFieldMapping
    val mappingTable =
        WebContifyCollectionDto(
            id = null,
            name = "mapping_${sourceCollection.name}_to_${referencedCollection.name}",
            displayName = "Mapping ${sourceCollection.name} to ${referencedCollection.name}",
            fields = fieldMappings.map { it.first })

    tableRepository.create(mappingTable, false)
    val mappingCollection = collectionRepository.create(mappingTable)
    return CollectionRelationMapping(
        id = mappingCollection.id!!,
        fieldsMapping =
            mappingCollection.fields!!
                .map { field ->
                  RelationFieldMapping(
                      field.id!!,
                      fieldMappings.map { it.second }.first { it.first == field.name }.second!!)
                }
                .toSet())
  }
}

package io.webcontify.backend.relations.handlers.impl

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.repositories.CollectionFieldRepository
import io.webcontify.backend.collections.repositories.CollectionRepository
import io.webcontify.backend.collections.repositories.CollectionTableRepository
import io.webcontify.backend.relations.*
import io.webcontify.backend.relations.handlers.RelationHandler
import io.webcontify.backend.relations.mappers.RelationMapper
import io.webcontify.backend.relations.models.*
import org.springframework.stereotype.Component

@Component(MANY_TO_MANY)
class ManyToManyRelationHandler(
    private val relationMapper: RelationMapper,
    private val tableRelationRepository: TableRelationRepository,
    private val collectionRepository: CollectionRepository,
    private val collectionFieldRepository: CollectionFieldRepository,
    private val tableRepository: CollectionTableRepository,
    private val relationRepository: RelationRepository
) : RelationHandler {
  // TODO change logic to mapping table is used instead of source and referenced. if mapping table
  override fun saveRelation(relation: CreateRelationDto): RelationDto {
    relation.mappingCollectionMapping ?: throw RuntimeException("mapping collection cannot be null")
    var mappingCollectionMapping = relation.mappingCollectionMapping
    if (mappingCollectionMapping.id == null) {
      mappingCollectionMapping = createMappingCollection(relation)
    }

    if (relation.sourceCollectionMapping.fieldsMapping.isNotEmpty()) {
      throw RuntimeException(
          "field mappings for many to many have to be defined in mappingCollectionMapping")
    }
    val referencedFieldsMapping =
        relation.mappingCollectionMapping.fieldsMapping
            .map {
              RelationFieldMapping(
                  it.referencedFieldId,
                  mappingCollectionMapping.fieldsMapping
                      .first { field -> field.referencedFieldId == it.referencedFieldId }
                      .sourceFieldId)
            }
            .toSet()
    val sourceFieldsMapping =
        relation.mappingCollectionMapping.fieldsMapping
            .map {
              RelationFieldMapping(
                  it.sourceFieldId,
                  mappingCollectionMapping.fieldsMapping
                      .first { field -> field.referencedFieldId == it.sourceFieldId }
                      .sourceFieldId)
            }
            .toSet()
    val relationDto =
        relationRepository.create(
            relation.copy(
                referencedCollectionMapping =
                    relation.referencedCollectionMapping.copy(
                        fieldsMapping = referencedFieldsMapping),
                mappingCollectionMapping = mappingCollectionMapping,
                sourceCollectionMapping =
                    relation.sourceCollectionMapping.copy(fieldsMapping = sourceFieldsMapping)))
    return relationDto
  }

  override fun createTableRelation(relation: RelationDto) {
    val mappingCollection =
        relation.mappingCollectionMapping ?: throw RuntimeException("cannot be null")
    val sourceTableRelation =
        relation.sourceCollectionMapping.let {
          relationMapper.toTableRelationDto(
              it.id!!, it.fieldsMapping.map { field -> field.sourceFieldId }.toSet())
        }
    val sourceTableMappingTableRelation =
        mappingCollection.let {
          relationMapper.toTableRelationDto(
              it.id!!,
              relation.sourceCollectionMapping.fieldsMapping
                  .map { field -> field.referencedFieldId }
                  .toSet())
        }
    val referencedTableRelation =
        relation.referencedCollectionMapping.let {
          relationMapper.toTableRelationDto(
              it.id!!, it.fieldsMapping.map { field -> field.sourceFieldId }.toSet())
        }
    val referencedTableMappingTableRelation =
        mappingCollection.let {
          relationMapper.toTableRelationDto(
              it.id!!,
              relation.referencedCollectionMapping.fieldsMapping
                  .map { field -> field.referencedFieldId }
                  .toSet())
        }
    tableRelationRepository.create(
        TableRelationDto(
            relationId = relation.id,
            sourceTable = referencedTableMappingTableRelation,
            referencedTable = referencedTableRelation))
    tableRelationRepository.create(
        TableRelationDto(
            relationId = relation.id,
            sourceTable = sourceTableMappingTableRelation,
            referencedTable = sourceTableRelation))
  }

  private fun createMappingCollection(
      relation: CreateRelationDto
  ): MappingCollectionRelationMapping {
    val sourceCollectionFields =
        relation.mappingCollectionMapping?.fieldsMapping?.map { it.sourceFieldId }?.toSet()
            ?: setOf()
    val sourceCollection =
        relationMapper.toTableRelationDto(
            relation.sourceCollectionMapping.id, sourceCollectionFields)
    val referencedCollectionFields =
        relation.mappingCollectionMapping?.fieldsMapping?.map { it.referencedFieldId }?.toSet()
            ?: setOf()
    val referencedCollection =
        relationMapper.toTableRelationDto(
            relation.referencedCollectionMapping.id, referencedCollectionFields)
    val sourceFieldMapping =
        sourceCollection.fields
            .map { fieldDto ->
              fieldDto.configuration?.unique = false
              fieldDto.configuration?.inValues = null
              fieldDto.configuration?.defaultValue = null
              val field =
                  fieldDto.copy(
                      name =
                          "ref${fieldDto.collectionId}${fieldDto.name.replaceFirstChar { it.uppercase() }}",
                      displayName = "Reference ${fieldDto.collectionId} ${fieldDto.displayName}")

              Pair(field, Pair(field.name, fieldDto.id))
            }
            .toMutableList()
    val referencedFieldMapping =
        referencedCollection.fields.map { fieldDto ->
          fieldDto.configuration?.unique = false
          fieldDto.configuration?.inValues = null
          fieldDto.configuration?.defaultValue = null
          val field =
              fieldDto.copy(
                  name =
                      "ref${fieldDto.collectionId}${fieldDto.name.replaceFirstChar { it.uppercase() }}",
                  displayName = "Reference ${fieldDto.collectionId} ${fieldDto.displayName}")
          Pair(field, Pair(field.name, fieldDto.id))
        }
    val mappingCollectionFieldMapping = sourceFieldMapping + referencedFieldMapping
    val mappingTable =
        WebContifyCollectionDto(
            id = null,
            name = "mapping_${sourceCollection.name}_to_${referencedCollection.name}",
            displayName = "Mapping ${sourceCollection.name} to ${referencedCollection.name}",
            fields = mappingCollectionFieldMapping.map { it.first.copy(isPrimaryKey = true) })

    tableRepository.create(mappingTable, false)
    val mappingCollection = collectionRepository.create(mappingTable)
    val mappingCollectionFields =
        mappingCollectionFieldMapping
            .map {
              collectionFieldRepository.create(it.first.copy(collectionId = mappingCollection.id))
            }
            .toList()
    val fieldsMapping =
        mappingCollectionFields
            .map { field ->
              RelationFieldMapping(
                  field.id!!,
                  mappingCollectionFieldMapping
                      .first { it.first.name == field.name }
                      .second
                      .second!!)
            }
            .toSet()
    return MappingCollectionRelationMapping(
        id = mappingCollection.id!!, name = mappingTable.name, fieldsMapping = fieldsMapping)
  }

  override fun deleteRelation(relation: RelationCollectionDto) {
    val mappingCollection = relation.mappingCollection!!
    val mappingToSourceTable =
        relation.copy(
            sourceCollection = mappingCollection, referencedCollection = relation.sourceCollection)
    tableRelationRepository.delete(mappingToSourceTable)
    val mappingToReferenceTable =
        relation.copy(
            sourceCollection = mappingCollection,
            referencedCollection = relation.referencedCollection)
    tableRelationRepository.delete(mappingToReferenceTable)
  }
}

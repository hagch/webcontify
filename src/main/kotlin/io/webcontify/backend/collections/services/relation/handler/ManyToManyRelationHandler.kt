package io.webcontify.backend.collections.services.relation.handler

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationDto
import io.webcontify.backend.collections.repositories.*
import io.webcontify.backend.collections.services.relation.RelationHandler
import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType
import org.springframework.stereotype.Service

@Service
class ManyToManyRelationHandler(
    val collectionRepository: CollectionRepository,
    val columnRepository: CollectionColumnRepository,
    val tableRepository: CollectionTableRepository,
    val tableRelationRepository: CollectionTableRelationRepository,
    val relationRepository: CollectionRelationRepository
) : RelationHandler {
  override fun getType(): WebcontifyCollectionRelationType {
    return WebcontifyCollectionRelationType.MANY_TO_MANY
  }

  private fun getReferenceColumnName(
      collection: WebContifyCollectionDto,
      column: WebContifyCollectionColumnDto
  ): String {
    return getReferenceColumnName(collection, column.name)
  }

  private fun getReferenceColumnName(
      collection: WebContifyCollectionDto,
      columnName: String
  ): String {
    return "ref_${collection.name}_${columnName}"
  }

  private fun getReferenceColumnDisplayName(
      collection: WebContifyCollectionDto,
      column: WebContifyCollectionColumnDto
  ): String {
    return "Reference ${collection.displayName} ${column.displayName}"
  }

  private fun prepareRelation(
      relation: WebContifyCollectionRelationDto
  ): Pair<WebContifyCollectionDto, WebContifyCollectionRelationDto> {
    val sourceCollection = relation.sourceCollection
    val referencedCollection = relation.referencedCollection
    val mappingCollection =
        WebContifyCollectionDto(
            id = null,
            name = "${sourceCollection.name}_${referencedCollection.name}_mapping",
            displayName =
                "${sourceCollection.displayName} ${referencedCollection.displayName} Mapping",
            columns =
                listOf(
                    *sourceCollection
                        .sourceRelationFields(relation.fields)
                        .map { col ->
                          col.copy(
                              name = getReferenceColumnName(sourceCollection, col),
                              displayName = getReferenceColumnDisplayName(sourceCollection, col))
                        }
                        .toTypedArray(),
                    *referencedCollection
                        .referencedRelationFields(relation.fields)
                        .map { col ->
                          col.copy(
                              name = getReferenceColumnName(referencedCollection, col),
                              displayName =
                                  getReferenceColumnDisplayName(referencedCollection, col))
                        }
                        .toTypedArray()),
            relations = listOf())
    val createdCollection = collectionRepository.create(mappingCollection)
    val createdCollectionWithColumns =
        createdCollection.copy(
            columns =
                mappingCollection.columns?.map { column ->
                  columnRepository.create(column.copy(collectionId = createdCollection.id))
                })
    tableRepository.create(createdCollectionWithColumns, false)
    return Pair(createdCollectionWithColumns, relation)
  }

  override fun createRelation(
      relation: WebContifyCollectionRelationDto
  ): WebContifyCollectionRelationDto {
    val preparedRelation = prepareRelation(relation)
    val manyToOneFromSourceToMappingCollectionFields =
        preparedRelation.second.fields
            .map {
              it.copy(
                  referencedCollectionColumnName =
                      getReferenceColumnName(
                          preparedRelation.second.sourceCollection, it.sourceCollectionColumnName))
            }
            .toSet()
    val manyToOneFromSourceToMappingCollection =
        preparedRelation.second.copy(
            referencedCollection = preparedRelation.first,
            fields = manyToOneFromSourceToMappingCollectionFields)
    createMirror(manyToOneFromSourceToMappingCollection)
    relationRepository.create(manyToOneFromSourceToMappingCollection)
    val manyToOneFromReferenceToMappingCollectionFields =
        preparedRelation.second.fields
            .map {
              it.copy(
                  referencedCollectionColumnName =
                      getReferenceColumnName(
                          preparedRelation.second.referencedCollection,
                          it.referencedCollectionColumnName),
                  sourceCollectionColumnName = it.referencedCollectionColumnName)
            }
            .toSet()
    val manyToOneFromReferenceToMappingCollection =
        preparedRelation.second.copy(
            sourceCollection = relation.referencedCollection,
            referencedCollection = preparedRelation.first,
            fields = manyToOneFromReferenceToMappingCollectionFields)
    relationRepository.create(manyToOneFromReferenceToMappingCollection)
    createMirror(manyToOneFromReferenceToMappingCollection)
    return relation.copy(
        referencedCollection = preparedRelation.first,
        fields =
            relation.fields
                .map {
                  it.copy(
                      referencedCollectionColumnName =
                          getReferenceColumnName(
                              relation.sourceCollection, it.sourceCollectionColumnName))
                }
                .toSet())
  }

  private fun createMirror(relation: WebContifyCollectionRelationDto) {
    val mirrorRelation = relation.switchReference(WebcontifyCollectionRelationType.MANY_TO_ONE)
    relationRepository.create(mirrorRelation)
    tableRelationRepository.create(mirrorRelation)
  }
}

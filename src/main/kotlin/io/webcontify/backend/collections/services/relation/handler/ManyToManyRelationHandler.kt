package io.webcontify.backend.collections.services.relation.handler

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationDto
import io.webcontify.backend.collections.repositories.*
import io.webcontify.backend.collections.services.relation.RelationHandler
import io.webcontify.backend.collections.utils.switchReferences
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

  private fun getReferenceColumnName(collection: WebContifyCollectionDto, column: WebContifyCollectionColumnDto): String {
    return getReferenceColumnName(collection,column.name)
  }

  private fun getReferenceColumnName(collection: WebContifyCollectionDto, columnName: String): String {
    return "ref_${collection.name}_${columnName}"
  }

  private fun getReferenceColumnDisplayName(collection: WebContifyCollectionDto, column: WebContifyCollectionColumnDto): String {
    return "Reference ${collection.displayName} ${column.displayName}"
  }

  private fun prepareRelation(
      relation: Set<WebContifyCollectionRelationDto>
  ): Pair<WebContifyCollectionDto,Set<WebContifyCollectionRelationDto>> {
    val sourceCollection = relation.first().sourceCollection
    val referencedCollection = relation.first().referencedCollection
    val mappingCollection =
        WebContifyCollectionDto(
            id = null,
            name = "${sourceCollection.name}_${referencedCollection.name}_mapping",
            displayName =
                "${sourceCollection.displayName} ${referencedCollection.displayName} Mapping",
            columns =
                listOf(
                    *sourceCollection
                        .sourceRelationFields(relation)
                        .map { col ->
                          col.copy(
                              name = getReferenceColumnName(sourceCollection, col),
                              displayName =
                                  getReferenceColumnDisplayName(sourceCollection, col))
                        }
                        .toTypedArray(),
                    *referencedCollection
                        .referencedRelationFields(relation)
                        .map { col ->
                          col.copy(
                              name = getReferenceColumnName(referencedCollection, col),
                              displayName =
                                  getReferenceColumnDisplayName(referencedCollection,col))
                        }
                        .toTypedArray()))
    val createdCollection = collectionRepository.create(mappingCollection)
    val createdCollectionWithColumns =
        createdCollection.copy(
            columns =
                mappingCollection.columns?.map { column ->
                  columnRepository.create(column.copy(collectionId = createdCollection.id))
                })
    tableRepository.create(createdCollectionWithColumns,false)
    return Pair(createdCollectionWithColumns,relation)
  }

  override fun createRelation(relation: Set<WebContifyCollectionRelationDto>) {
    val preparedRelation = prepareRelation(relation)
    val manyToOneFromSourceToMappingCollection =
        preparedRelation
            .second.map {
              it.copy(
                  referencedCollection = preparedRelation.first,
                   referencedCollectionColumnName =
                      getReferenceColumnName(it.sourceCollection, it.sourceCollectionColumnName))
            }
            .toSet()
    createMirror(manyToOneFromSourceToMappingCollection)
    relationRepository.create(manyToOneFromSourceToMappingCollection)
    val manyToOneFromReferenceToMappingCollection =
        preparedRelation
            .second.map {
              it.copy(
                  sourceCollection = it.referencedCollection,
                  sourceCollectionColumnName = it.referencedCollectionColumnName,
                referencedCollection = preparedRelation.first,
                referencedCollectionColumnName = getReferenceColumnName(it.referencedCollection, it.referencedCollectionColumnName))
            }
            .toSet()
    relationRepository.create(manyToOneFromReferenceToMappingCollection)
    createMirror(manyToOneFromReferenceToMappingCollection)
  }

  private fun createMirror(relation: Set<WebContifyCollectionRelationDto>) {
    val mirrorRelation = relation.switchReferences(WebcontifyCollectionRelationType.MANY_TO_MANY)
    relationRepository.create(mirrorRelation)
    tableRelationRepository.create(mirrorRelation)
  }
}

package io.webcontify.backend.views

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType
import io.webcontify.backend.jooq.enums.WebcontifyViewAggregationType
import io.webcontify.backend.relations.*
import org.mapstruct.Mapper
import org.springframework.beans.factory.annotation.Autowired

@Mapper(componentModel = "spring")
abstract class ViewMapper {

  @Autowired private lateinit var relationRepository: RelationRepository

  fun mapApiToDto(request: ViewCreateRequestDto, sourceCollectionId: Long): ViewDto {
    val relationInfos = relationRepository.getInfoByIds(request.getRelationIds())
    val relationInfo = relationInfos.first()
    val sourceCollection = getReferencedCollection(sourceCollectionId, relationInfo)
    return ViewDto(
        null,
        request.name,
        RelationTree(
            sourceCollection,
            referencedCollectionRelationName = "",
            null,
            aggregationType = null,
            buildTree(request.relations, sourceCollectionId, relationInfos),
            true,
            emptySet()))
  }

  private fun buildTree(
      relationTree: List<ViewRelationTreeRequestDto>,
      sourceCollectionId: Long,
      relations: List<RelationInfoDto>
  ): List<RelationTree> {
    return relationTree.map { relation ->
      val relationInfo = relations.first { it.id == relation.relationId }
      val sourceCollection = getReferencedCollection(sourceCollectionId, relationInfo)
      val referencedCollection = getReferencedCollection(relation.collectionId, relationInfo)
      val sourceCollectionRelationFields =
          sourceCollection.fields?.filter { field ->
            !field.isPrimaryKey && relationInfo.fieldMapping.any { it.sourceFieldId == field.id }
          } ?: emptyList()
      val isMandatory: Boolean =
          if (sourceCollectionRelationFields.isNotEmpty()) {
            sourceCollectionRelationFields.all { it.configuration?.nullable == false }
          } else {
            false
          }
      val fields =
          relationInfo.fieldMapping
              .filter { field ->
                sourceCollection.fields?.any { it.id == field.sourceFieldId } == true &&
                    referencedCollection.fields?.any { it.id == field.referencedFieldId } == true
              }
              .toSet()
      val aggregationType =
          identifyRelationAggregationType(referencedCollection, relationInfo, sourceCollectionId)
      return@map RelationTree(
          referencedCollection,
          getReferencedCollectionName(relation.collectionId, relationInfo),
          relationInfo.id,
          aggregationType,
          buildTree(relation.childRelations, referencedCollection.id!!, relations),
          isMandatory,
          fields)
    }
  }

  private fun identifyRelationAggregationType(
      referencedCollection: WebContifyCollectionDto,
      relationInfo: RelationInfoDto,
      sourceCollectionId: Long
  ) =
      if (isMappingTable(referencedCollection.id!!, relationInfo) ||
          isOneToManyJoin(relationInfo, sourceCollectionId) ||
          isReverseManyToOneJoin(relationInfo, sourceCollectionId)) {
        WebcontifyViewAggregationType.LIST
      } else {
        WebcontifyViewAggregationType.OBJECT
      }

  private fun isReverseManyToOneJoin(relationInfo: RelationInfoDto, sourceCollectionId: Long) =
      (relationInfo.type == WebcontifyCollectionRelationType.MANY_TO_ONE &&
          sourceCollectionId == relationInfo.referencedCollection.id)

  private fun isOneToManyJoin(relationInfo: RelationInfoDto, sourceCollectionId: Long) =
      (relationInfo.type == WebcontifyCollectionRelationType.ONE_TO_MANY &&
          sourceCollectionId == relationInfo.sourceCollection.id)

  private fun getReferencedCollection(
      collectionId: Long,
      relationInfo: RelationInfoDto
  ): WebContifyCollectionDto {
    return if (relationInfo.mappingCollection?.id == collectionId) {
      relationInfo.mappingCollection
    } else if (relationInfo.referencedCollection.id == collectionId) {
      relationInfo.referencedCollection
    } else {
      relationInfo.sourceCollection
    }
  }

  private fun isMappingTable(collectionId: Long, relationInfo: RelationInfoDto): Boolean {
    return relationInfo.mappingCollection?.id == collectionId
  }

  private fun getReferencedCollectionName(
      collectionId: Long,
      relationInfo: RelationInfoDto
  ): String {
    return if (relationInfo.mappingCollection?.id == collectionId) {
      relationInfo.mappingCollectionRelationName!!
    } else if (relationInfo.referencedCollection.id == collectionId) {
      relationInfo.referencedCollectionRelationName
    } else {
      relationInfo.sourceCollectionRelationName
    }
  }
}

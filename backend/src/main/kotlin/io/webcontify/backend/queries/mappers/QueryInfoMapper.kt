package io.webcontify.backend.queries.mappers

import io.webcontify.backend.collections.mappers.CollectionMapper
import io.webcontify.backend.jooq.tables.records.*
import io.webcontify.backend.jooq.tables.references.*
import io.webcontify.backend.queries.models.QueryInfo
import io.webcontify.backend.queries.models.QueryRelationInfoTree
import io.webcontify.backend.relations.RelationFieldMapping
import java.util.stream.Collectors
import org.jooq.Record
import org.jooq.SelectConditionStep
import org.springframework.stereotype.Component

@Component
class QueryInfoMapper(private val collectionMapper: CollectionMapper) {

  private final val sourceCollection = WEBCONTIFY_COLLECTION.`as`("sourceCollection")
  private final val relationCollection = WEBCONTIFY_COLLECTION.`as`("relationCollection")
  private final val sourceFields = WEBCONTIFY_COLLECTION_FIELD.`as`("sourceCollectionFields")
  private final val relationFields = WEBCONTIFY_COLLECTION_FIELD.`as`("relationCollectionFields")

  data class MappingHelper(
      val sourceCollection: WebcontifyCollectionRecord?,
      val sourceField: WebcontifyCollectionFieldRecord?,
      val relationCollection: WebcontifyCollectionRecord?,
      val relationField: WebcontifyCollectionFieldRecord?,
      val collectionRelation: WebcontifyCollectionRelationRecord?,
      val viewRelation: WebcontifyQueryRelationRecord?,
      val viewRelationFieldMapping: WebcontifyCollectionRelationFieldRecord?
  )

  data class ConntectionHelper(
      val relationCollection: WebcontifyCollectionRecord,
      val relationCollectionFields: List<WebcontifyCollectionFieldRecord>,
      val relation: WebcontifyCollectionRelationRecord,
      val relationFieldMappings: Set<WebcontifyCollectionRelationFieldRecord>
  )

  fun mapToWebcontifyViewDTO(records: SelectConditionStep<Record>): QueryInfo {
    val viewRecords = records.stream().collect(Collectors.groupingBy { it.into(WEBCONTIFY_QUERY) })
    var record = viewRecords.entries.first()
    val mappingHelper =
        record.value.map {
          MappingHelper(
              sourceCollection = it.into(sourceCollection),
              sourceField = it.into(sourceFields),
              relationCollection = it.into(relationCollection),
              relationField = it.into(relationFields),
              collectionRelation = it.into(WEBCONTIFY_COLLECTION_RELATION),
              viewRelation = it.into(WEBCONTIFY_QUERY_RELATION),
              viewRelationFieldMapping =
                  it.into(WebcontifyCollectionRelationFieldRecord::class.java))
        }
    val sourceCollection =
        mappingHelper
            .groupBy { it.sourceCollection }
            .map { it.key!! to it.value.map { it.sourceField!! } }
            .first()
    var viewRelation =
        mappingHelper
            .groupBy { it.viewRelation }
            .map { it.key to mapToConnectionHelper(it.value, it.key!!) }

    return QueryInfo(
        name = record.key.name!!,
        root =
            QueryRelationInfoTree(
                collection =
                    collectionMapper.mapToDto(
                        sourceCollection.first, sourceCollection.second.toSet()),
                type = null,
                relation = null,
                fieldMappings = emptySet(),
                childRelationTrees = buildTree(null, viewRelation)))
  }

  private fun buildTree(
      relationParentId: Long?,
      relations: List<Pair<WebcontifyQueryRelationRecord?, ConntectionHelper>>
  ): List<QueryRelationInfoTree> {
    return relations
        .filter { it.first?.viewRelationParentId == relationParentId }
        .map { (relation, connectionHelper) ->
          return@map QueryRelationInfoTree(
              collection =
                  collectionMapper.mapToDto(
                      connectionHelper.relationCollection,
                      connectionHelper.relationCollectionFields.toSet()),
              type = relation?.aggregationType,
              mandatory = relation?.mandatory ?: false,
              relation = connectionHelper.relation,
              fieldMappings =
                  connectionHelper.relationFieldMappings
                      .map { RelationFieldMapping(it.sourceFieldId!!, it.referencedFieldId!!) }
                      .toSet(),
              childRelationTrees = buildTree(relation?.id, relations))
        }
  }

  private fun mapToConnectionHelper(
      helpers: List<MappingHelper>,
      viewRelation: WebcontifyQueryRelationRecord
  ): ConntectionHelper {
    val relationCollection =
        helpers.first { it.relationCollection?.id == viewRelation.collectionId }.relationCollection
    val relationFields =
        helpers
            .filter { it.relationField?.collectionId == relationCollection?.id }
            .mapNotNull { it.relationField }
            .toSet()
    val relation = helpers.first { it.collectionRelation != null }.collectionRelation
    val viewRelationFields =
        helpers
            .mapNotNull { it.viewRelationFieldMapping }
            .filter {
              it.relationId == relation?.id &&
                  it.referencedFieldId != null &&
                  it.sourceFieldId != null
            }
            .toSet()
    return ConntectionHelper(
        relationCollection!!, relationFields.toList(), relation!!, viewRelationFields)
  }
}

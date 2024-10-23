package io.webcontify.backend.views

import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.mappers.CollectionMapper
import io.webcontify.backend.collections.models.Item
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.services.field.handler.FieldHandlerStrategy
import io.webcontify.backend.jooq.enums.WebcontifyViewAggregationType
import io.webcontify.backend.jooq.tables.records.*
import io.webcontify.backend.jooq.tables.references.*
import java.util.stream.Collectors
import org.jooq.*
import org.jooq.impl.DSL.asterisk
import org.jooq.impl.DSL.name
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Repository

@Repository
class ViewRepository(
    private val dslContext: DSLContext,
    private val collectionMapper: CollectionMapper,
    private val fieldHandlerStrategy: FieldHandlerStrategy
) {

  private final val sourceCollection = WEBCONTIFY_COLLECTION.`as`("sourceCollection")
  private final val relationCollection = WEBCONTIFY_COLLECTION.`as`("relationCollection")
  private final val sourceFields = WEBCONTIFY_COLLECTION_FIELD.`as`("sourceCollectionFields")
  private final val relationFields = WEBCONTIFY_COLLECTION_FIELD.`as`("relationCollectionFields")

  fun createView(viewDto: ViewDto): Long {
    dslContext.createView(name(viewDto.name)).`as`(viewDto.relation.getQuery(dslContext)).execute()
    val record =
        dslContext.newRecord(WEBCONTIFY_VIEW).apply {
          this.displayName = viewDto.name
          this.name = viewDto.name
          this.sourceCollectionId = viewDto.relation.fromCollection.id
        }
    try {
      record.insert()
    } catch (e: DuplicateKeyException) {
      throw AlreadyExistsException(
          ErrorCode.COLLECTION_WITH_NAME_ALREADY_EXISTS, record.name.toString())
    }
    saveRelation(viewDto.relation.toChildren, record.id!!, null)
    return record.id!!
  }

  fun getById(id: Long): ViewInfo {
    var result =
        dslContext
            .select()
            .from(WEBCONTIFY_VIEW)
            .innerJoin(sourceCollection)
            .on(sourceCollection.ID.eq(WEBCONTIFY_VIEW.SOURCE_COLLECTION_ID))
            .innerJoin(sourceFields)
            .on(sourceFields.COLLECTION_ID.eq(sourceCollection.ID))
            .innerJoin(WEBCONTIFY_VIEW_RELATION)
            .on(WEBCONTIFY_VIEW.ID.eq(WEBCONTIFY_VIEW_RELATION.VIEW_ID))
            .innerJoin(relationCollection)
            .on(relationCollection.ID.eq(WEBCONTIFY_VIEW_RELATION.COLLECTION_ID))
            .innerJoin(relationFields)
            .on(relationFields.COLLECTION_ID.eq(relationCollection.ID))
            .innerJoin(WEBCONTIFY_COLLECTION_RELATION)
            .on(WEBCONTIFY_VIEW_RELATION.RELATION_ID.eq(WEBCONTIFY_COLLECTION_RELATION.ID))
            .where(WEBCONTIFY_VIEW.ID.eq(id))
    return mapToWebcontifyViewDTO(result)
  }

  data class MappingHelper(
      val sourceCollection: WebcontifyCollectionRecord?,
      val sourceField: WebcontifyCollectionFieldRecord?,
      val relationCollection: WebcontifyCollectionRecord?,
      val relationField: WebcontifyCollectionFieldRecord?,
      val collectionRelation: WebcontifyCollectionRelationRecord?,
      val viewRelation: WebcontifyViewRelationRecord?
  )

  data class ConntectionHelper(
      val relationCollection: WebcontifyCollectionRecord,
      val relationCollectionFields: List<WebcontifyCollectionFieldRecord>,
      val relation: WebcontifyCollectionRelationRecord
  )

  fun mapToWebcontifyViewDTO(records: SelectConditionStep<Record>): ViewInfo {
    val viewRecords = records.stream().collect(Collectors.groupingBy { it.into(WEBCONTIFY_VIEW) })
    var record = viewRecords.entries.first()
    val mappingHelper =
        record.value.map {
          MappingHelper(
              sourceCollection = it.into(sourceCollection),
              sourceField = it.into(sourceFields),
              relationCollection = it.into(relationCollection),
              relationField = it.into(relationFields),
              collectionRelation = it.into(WEBCONTIFY_COLLECTION_RELATION),
              viewRelation = it.into(WEBCONTIFY_VIEW_RELATION))
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

    return ViewInfo(
        name = record.key.name!!,
        root =
            ViewRelationInfoTree(
                collection =
                    collectionMapper.mapToDto(
                        sourceCollection.first, sourceCollection.second.toSet()),
                type = null,
                relation = null,
                childRelationTrees = buildTree(null, viewRelation)))
  }

  private fun buildTree(
      relationParentId: Long?,
      relations: List<Pair<WebcontifyViewRelationRecord?, ConntectionHelper>>
  ): List<ViewRelationInfoTree> {
    return relations
        .filter { it.first?.viewRelationParentId == relationParentId }
        .map { (relation, connectionHelper) ->
          return@map ViewRelationInfoTree(
              collection =
                  collectionMapper.mapToDto(
                      connectionHelper.relationCollection,
                      connectionHelper.relationCollectionFields.toSet()),
              type = relation?.aggregationType,
              relation = connectionHelper.relation,
              childRelationTrees = buildTree(relation?.id, relations))
        }
  }

  data class ViewInfo(val name: String, val root: ViewRelationInfoTree)

  data class ViewRelationInfoTree(
      val collection: WebContifyCollectionDto,
      val relation: WebcontifyCollectionRelationRecord?,
      val type: WebcontifyViewAggregationType?,
      val childRelationTrees: List<ViewRelationInfoTree>
  ) {
    fun getName(prefix: String?): String {
      if (prefix != null) {
        return "${prefix}_${collection.id.toString()}"
      }
      return collection.id.toString()
    }

    fun hasListRelation(): Boolean {
      return childRelationTrees.any { it.type == WebcontifyViewAggregationType.LIST }
    }

    fun getRelationCollectionName(info: ViewRelationInfoTree?): String {
      if (info?.collection?.id == info?.relation?.sourceCollectionId) {
        return info?.relation?.sourceRelationName!!
      }
      if (info?.collection?.id == info?.relation?.referencedCollectionId) {
        return info?.relation?.referencedRelationName!!
      }
      return info?.relation?.mappingRelationName ?: ""
    }
  }

  fun mapToConnectionHelper(
      helpers: List<MappingHelper>,
      viewRelation: WebcontifyViewRelationRecord
  ): ConntectionHelper {
    var relationCollection =
        helpers.first { it.relationCollection?.id == viewRelation.collectionId }.relationCollection
    var relationFields =
        helpers
            .filter { it.relationField?.collectionId == relationCollection?.id }
            .mapNotNull { it.relationField }
            .toSet()
    val relation = helpers.first { it.collectionRelation != null }.collectionRelation
    return ConntectionHelper(relationCollection!!, relationFields.toList(), relation!!)
  }

  fun getAllViewItems(viewInfo: ViewInfo): List<Item> {
    val maps = dslContext.select(asterisk()).from(name(viewInfo.name)).fetchMaps()
    return aggregateToItems(maps, viewInfo.root, null, fieldHandlerStrategy)
  }

  fun aggregateToItems(
      entries: List<Item>,
      root: ViewRelationInfoTree,
      prefix: String?,
      fieldHandlerStrategy: FieldHandlerStrategy
  ): List<Item> {
    val fields = root.collection.fields ?: emptyList()
    val grouped =
        entries
            .stream()
            .collect(
                Collectors.groupingBy(
                    { entry ->
                      fields.associate { field ->
                        field.name to entry[root.getName(prefix) + field.name]
                      }
                    },
                    Collectors.toList()))
            .filter { group ->
              !group.key
                  .filter { entry ->
                    root.collection.getPrimaryFields().any { entry.key == it.name }
                  }
                  .values
                  .contains(null)
            }
    return grouped.map { getGroupMappedToItem(fieldHandlerStrategy, root, it, prefix) }.toList()
  }

  private fun getGroupMappedToItem(
      fieldHandlerStrategy: FieldHandlerStrategy,
      root: ViewRelationInfoTree,
      entry: Map.Entry<Map<String, Any?>, MutableList<Item>>,
      prefix: String?
  ): MutableMap<String, Any?> {
    val map =
        fieldHandlerStrategy.castItemToJavaTypes(root.collection.fields, entry.key).toMutableMap()
    val children =
        root.childRelationTrees.associate {
          it.let { child ->
            val key = child.getRelationCollectionName(child)
            val aggregatedItems =
                aggregateToItems(entry.value, child, root.getName(prefix), fieldHandlerStrategy)
            val value: Any? =
                if (child.type == WebcontifyViewAggregationType.OBJECT) {
                  if (aggregatedItems.isEmpty()) {
                    null
                  } else {
                    aggregatedItems.first()
                  }
                } else {
                  aggregatedItems
                }
            return@let key to value
          }
        }
    map.putAll(children)
    return map
  }

  private fun saveRelation(relations: List<RelationTree>, id: Long, relationParentId: Long?) {
    val savedRelationMap =
        relations.map {
          val record =
              dslContext.newRecord(WEBCONTIFY_VIEW_RELATION).apply {
                this.viewId = id
                this.collectionId = it.fromCollection.id
                this.relationId = it.relationId!!
                this.aggregationType = it.aggregationType
                this.viewRelationParentId = relationParentId
              }
          record.insert()
          return@map it to record.id
        }
    savedRelationMap.forEach { saveRelation(it.first.toChildren, id, it.second) }
  }
}

package io.webcontify.backend.queries

import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.models.Item
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.jooq.tables.references.*
import io.webcontify.backend.queries.mappers.QueryInfoMapper
import io.webcontify.backend.queries.models.QueryDto
import io.webcontify.backend.queries.models.QueryInfo
import io.webcontify.backend.queries.models.QueryRelationTree
import org.jooq.*
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Repository

@Repository
class QueryRepository(
    private val dslContext: DSLContext,
    private val queryInfoMapper: QueryInfoMapper,
    private val queryExecutor: QueryExecutor
) {

  private final val sourceCollection = WEBCONTIFY_COLLECTION.`as`("sourceCollection")
  private final val relationCollection = WEBCONTIFY_COLLECTION.`as`("relationCollection")
  private final val sourceFields = WEBCONTIFY_COLLECTION_FIELD.`as`("sourceCollectionFields")
  private final val relationFields = WEBCONTIFY_COLLECTION_FIELD.`as`("relationCollectionFields")

  fun createView(queryDto: QueryDto): Long {
    val record =
        dslContext.newRecord(WEBCONTIFY_QUERY).apply {
          this.displayName = queryDto.name
          this.name = queryDto.name
          this.sourceCollectionId = queryDto.relation.fromCollection.id
        }
    try {
      record.insert()
    } catch (e: DuplicateKeyException) {
      throw AlreadyExistsException(
          ErrorCode.COLLECTION_WITH_NAME_ALREADY_EXISTS, record.name.toString())
    }
    saveRelation(queryDto.relation.toChildren, record.id!!, null)
    return record.id!!
  }

  fun getById(id: Long): QueryInfo {
    var result =
        dslContext
            .select()
            .from(WEBCONTIFY_QUERY)
            .innerJoin(sourceCollection)
            .on(sourceCollection.ID.eq(WEBCONTIFY_QUERY.SOURCE_COLLECTION_ID))
            .innerJoin(sourceFields)
            .on(sourceFields.COLLECTION_ID.eq(sourceCollection.ID))
            .innerJoin(WEBCONTIFY_QUERY_RELATION)
            .on(WEBCONTIFY_QUERY.ID.eq(WEBCONTIFY_QUERY_RELATION.VIEW_ID))
            .innerJoin(relationCollection)
            .on(relationCollection.ID.eq(WEBCONTIFY_QUERY_RELATION.COLLECTION_ID))
            .innerJoin(relationFields)
            .on(relationFields.COLLECTION_ID.eq(relationCollection.ID))
            .innerJoin(WEBCONTIFY_COLLECTION_RELATION)
            .on(WEBCONTIFY_QUERY_RELATION.RELATION_ID.eq(WEBCONTIFY_COLLECTION_RELATION.ID))
            .innerJoin(WEBCONTIFY_COLLECTION_RELATION_FIELD)
            .on(
                WEBCONTIFY_COLLECTION_RELATION_FIELD.RELATION_ID.eq(
                    WEBCONTIFY_COLLECTION_RELATION.ID))
            .where(WEBCONTIFY_QUERY.ID.eq(id))
    return queryInfoMapper.mapToWebcontifyViewDTO(result)
  }

  fun getAllViewItems(queryInfo: QueryInfo): List<Item> {
    return queryExecutor.getAllForView(queryInfo.root)
  }

  private fun saveRelation(relations: List<QueryRelationTree>, id: Long, relationParentId: Long?) {
    val savedRelationMap =
        relations.map {
          val record =
              dslContext.newRecord(WEBCONTIFY_QUERY_RELATION).apply {
                this.viewId = id
                this.collectionId = it.fromCollection.id
                this.relationId = it.relationId!!
                this.aggregationType = it.aggregationType
                this.viewRelationParentId = relationParentId
                this.mandatory = it.isMandatory
              }
          record.insert()
          return@map it to record.id
        }
    savedRelationMap.forEach { saveRelation(it.first.toChildren, id, it.second) }
  }
}

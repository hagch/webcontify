package io.webcontify.backend.queries.models

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.jooq.enums.WebcontifyQueryAggregationType
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionRelationRecord
import io.webcontify.backend.relations.RelationFieldMapping

data class QueryRelationInfoTree(
    val collection: WebContifyCollectionDto,
    val relation: WebcontifyCollectionRelationRecord?,
    val type: WebcontifyQueryAggregationType?,
    val mandatory: Boolean = false,
    val childRelationTrees: List<QueryRelationInfoTree>,
    val fieldMappings: Set<RelationFieldMapping>
) {
  fun getName(prefix: String?): String {
    if (prefix != null) {
      return "${prefix}_${collection.id.toString()}"
    }
    return collection.id.toString()
  }

  fun getRelationCollectionName(info: QueryRelationInfoTree?): String {
    if (info?.collection?.id == info?.relation?.sourceCollectionId) {
      return info?.relation?.sourceRelationName!!
    }
    if (info?.collection?.id == info?.relation?.referencedCollectionId) {
      return info?.relation?.referencedRelationName!!
    }
    return info?.relation?.mappingRelationName ?: ""
  }
}

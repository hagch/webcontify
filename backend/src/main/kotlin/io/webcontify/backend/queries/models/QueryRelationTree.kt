package io.webcontify.backend.queries.models

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.jooq.enums.WebcontifyQueryAggregationType
import io.webcontify.backend.relations.models.RelationFieldMapping

data class QueryRelationTree(
    val fromCollection: WebContifyCollectionDto,
    val referencedCollectionRelationName: String,
    val relationId: Long?,
    val aggregationType: WebcontifyQueryAggregationType?,
    val toChildren: List<QueryRelationTree>,
    val isMandatory: Boolean,
    val fieldMapping: Set<RelationFieldMapping>
)

package io.webcontify.backend.queries.models

data class QueryRelationTreeRequestDto(
    val relationId: Long,
    val collectionId: Long,
    val childRelations: List<QueryRelationTreeRequestDto>
) {
  fun getRelationIds(): List<Long> {
    return listOf(relationId) + childRelations.flatMap { it.getRelationIds() }
  }
}

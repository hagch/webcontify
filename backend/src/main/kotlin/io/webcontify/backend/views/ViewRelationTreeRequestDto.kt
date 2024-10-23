package io.webcontify.backend.views

data class ViewRelationTreeRequestDto(
    val relationId: Long,
    val collectionId: Long,
    val childRelations: List<ViewRelationTreeRequestDto>
) {
  fun getRelationIds(): List<Long> {
    return listOf(relationId) + childRelations.flatMap { it.getRelationIds() }
  }
}

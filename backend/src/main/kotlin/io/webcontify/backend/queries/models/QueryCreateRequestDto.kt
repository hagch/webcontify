package io.webcontify.backend.queries.models

data class QueryCreateRequestDto(
    val name: String,
    val relations: List<QueryRelationTreeRequestDto>
) {

  fun getRelationIds(): List<Long> {
    return relations.flatMap { it.getRelationIds() }
  }
}

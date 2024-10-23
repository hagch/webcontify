package io.webcontify.backend.views

data class ViewCreateRequestDto(val name: String, val relations: List<ViewRelationTreeRequestDto>) {

  fun getRelationIds(): List<Long> {
    return relations.flatMap { it.getRelationIds() }
  }
}

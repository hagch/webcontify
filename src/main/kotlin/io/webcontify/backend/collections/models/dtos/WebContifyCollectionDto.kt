package io.webcontify.backend.collections.models.dtos

import io.webcontify.backend.collections.models.Item
import io.webcontify.backend.collections.utils.snakeToCamelCase

data class WebContifyCollectionDto(
    val id: Long?,
    val name: String,
    val displayName: String = name,
    val columns: List<WebContifyCollectionColumnDto>? = listOf(),
    val relations: List<WebContifyCollectionRelationIdDto>? = listOf()
) {

  fun primaryColumnItemValueString(item: Item): String {
    return columns
        ?.filter { it.isPrimaryKey }
        ?.joinToString { "${it.name.snakeToCamelCase()}= ${item[it.name.snakeToCamelCase()]}" }
        .toString()
  }

  fun getColumnWithName(name: String): WebContifyCollectionColumnDto? {
    return this.columns?.firstOrNull { it.name == name }
  }

  fun sourceRelationFields(
      fields: Set<WebContifyCollectionRelationFieldDto>
  ): Set<WebContifyCollectionColumnDto> {
    return columns
        ?.filter { fields.find { field -> field.sourceCollectionColumnId == it.id } != null }
        ?.toSet()
        ?: setOf()
  }

  fun referencedRelationFields(
      fields: Set<WebContifyCollectionRelationFieldDto>
  ): Set<WebContifyCollectionColumnDto> {
    return columns
        ?.filter { fields.find { field -> field.referencedCollectionColumnId == it.id } != null }
        ?.toSet()
        ?: setOf()
  }
}

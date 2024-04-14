package io.webcontify.backend.collections.models.dtos

import io.webcontify.backend.collections.models.Item
import io.webcontify.backend.collections.utils.snakeToCamelCase

data class WebContifyCollectionDto(
    val id: Long?,
    val name: String,
    val displayName: String = name,
    val fields: List<WebContifyCollectionFieldDto>? = listOf(),
    val relations: List<WebContifyCollectionRelationIdDto>? = listOf()
) {

  fun primaryFieldItemValueString(item: Item): String {
    return fields
        ?.filter { it.isPrimaryKey }
        ?.joinToString { "${it.name.snakeToCamelCase()}= ${item[it.name.snakeToCamelCase()]}" }
        .toString()
  }

  fun getFieldWithName(name: String): WebContifyCollectionFieldDto? {
    return this.fields?.firstOrNull { it.name == name }
  }

  fun sourceRelationFields(
      fields: Set<WebContifyCollectionRelationFieldDto>
  ): Set<WebContifyCollectionFieldDto> {
    return this.fields
        ?.filter { fields.find { field -> field.sourceCollectionFieldId == it.id } != null }
        ?.toSet()
        ?: setOf()
  }

  fun referencedRelationFields(
      fields: Set<WebContifyCollectionRelationFieldDto>
  ): Set<WebContifyCollectionFieldDto> {
    return this.fields
        ?.filter { fields.find { field -> field.referencedCollectionFieldId == it.id } != null }
        ?.toSet()
        ?: setOf()
  }
}

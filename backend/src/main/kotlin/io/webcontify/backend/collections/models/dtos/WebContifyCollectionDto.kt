package io.webcontify.backend.collections.models.dtos

import io.webcontify.backend.collections.models.Item

data class WebContifyCollectionDto(
    val id: Long?,
    val name: String,
    val displayName: String = name,
    val fields: List<WebContifyCollectionFieldDto>? = listOf()
) {

  fun primaryFieldItemValueString(item: Item): String {
    return fields
        ?.filter { it.isPrimaryKey }
        ?.joinToString { "${it.name}= ${item[it.name]}" }
        .toString()
  }

  fun getFieldWithName(name: String): WebContifyCollectionFieldDto? {
    return this.fields?.firstOrNull { it.name == name }
  }

  fun getFieldWithId(id: Long): WebContifyCollectionFieldDto? {
    return this.fields?.firstOrNull { it.id == id }
  }

  fun getPrimaryFields(): List<WebContifyCollectionFieldDto> {
    return this.fields?.filter { it.isPrimaryKey } ?: emptyList()
  }
}

package io.webcontify.backend.collections.models.dtos

import io.webcontify.backend.collections.models.Item
import io.webcontify.backend.collections.utils.snakeToCamelCase
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType

data class WebContifyCollectionDto(
    val id: Long?,
    val name: String,
    val displayName: String = name,
    val fields: List<WebContifyCollectionFieldDto>? = listOf()
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

  fun queryAbleFields(): List<WebContifyCollectionFieldDto> {
    return this.fields?.filter { it.type != WebcontifyCollectionFieldType.RELATION_MIRROR }
        ?: listOf()
  }
}

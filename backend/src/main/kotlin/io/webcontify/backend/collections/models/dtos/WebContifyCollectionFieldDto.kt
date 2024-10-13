package io.webcontify.backend.collections.models.dtos

import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType

data class WebContifyCollectionFieldDto(
    val id: Long?,
    val collectionId: Long?,
    val name: String,
    val displayName: String = name,
    val type: WebcontifyCollectionFieldType,
    val isPrimaryKey: Boolean,
    val configuration: WebContifyCollectionFieldConfigurationDto<Any>?
) {

  fun isUpdateAble(newField: WebContifyCollectionFieldDto): Boolean {
    if (this.isPrimaryKey) {
      return this.type == newField.type && newField.isPrimaryKey && this.name == newField.name
    }
    return this.type == newField.type && !newField.isPrimaryKey
  }
}

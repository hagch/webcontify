package io.webcontify.backend.collections.models.dtos

import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType

data class WebContifyCollectionColumnDto(
    val collectionId: Int?,
    val name: String,
    val displayName: String = name,
    val type: WebcontifyCollectionColumnType,
    val isPrimaryKey: Boolean,
    val configuration: WebContifyCollectionColumnConfigurationDto<Any>?
) {

  fun isUpdateAble(newColumn: WebContifyCollectionColumnDto): Boolean {
    return this.type == newColumn.type && this.isPrimaryKey == newColumn.isPrimaryKey
  }
}

package io.webcontify.backend.collections.models.dtos

import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType

data class WebContifyCollectionColumnDto(
    var collectionId: Int?,
    val name: String,
    val displayName: String = name,
    val type: WebcontifyCollectionColumnType,
    val isPrimaryKey: Boolean
) {

  fun isUpdateAble(newColumn: WebContifyCollectionColumnDto): Boolean {
    return this.type == newColumn.type && this.isPrimaryKey == newColumn.isPrimaryKey
  }
}

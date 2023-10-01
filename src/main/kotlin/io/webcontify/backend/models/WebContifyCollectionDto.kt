package io.webcontify.backend.models

import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType

data class WebContifyCollectionDto(
    val id: Int?,
    val name: String,
    val displayName: String?,
    val columns: List<WebContifyCollectionColumnDto>?
)

data class WebContifyCollectionColumnDto(
    val collectionId: Int?,
    val name: String,
    val displayName: String,
    val type: WebcontifyCollectionColumnType,
    val isPrimaryKey: Boolean
)

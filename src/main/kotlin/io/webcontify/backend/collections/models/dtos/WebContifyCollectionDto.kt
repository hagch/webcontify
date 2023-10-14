package io.webcontify.backend.collections.models.dtos

import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType

data class WebContifyCollectionDto(
    val id: Int?,
    val name: String,
    val displayName: String = name,
    val columns: List<WebContifyCollectionColumnDto> = listOf()
)

data class WebContifyCollectionColumnDto(
    val collectionId: Int?,
    val name: String,
    val displayName: String = name,
    val type: WebcontifyCollectionColumnType,
    val isPrimaryKey: Boolean
)

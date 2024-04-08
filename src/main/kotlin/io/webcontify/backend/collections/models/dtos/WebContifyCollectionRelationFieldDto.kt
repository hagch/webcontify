package io.webcontify.backend.collections.models.dtos

data class WebContifyCollectionRelationFieldDto(
    val sourceCollectionColumnId: Long,
    val referencedCollectionColumnId: Long
)

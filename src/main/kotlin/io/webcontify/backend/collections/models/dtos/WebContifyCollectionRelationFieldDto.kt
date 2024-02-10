package io.webcontify.backend.collections.models.dtos

data class WebContifyCollectionRelationFieldDto(
    val sourceCollectionColumnName: String,
    val referencedCollectionColumnName: String
)

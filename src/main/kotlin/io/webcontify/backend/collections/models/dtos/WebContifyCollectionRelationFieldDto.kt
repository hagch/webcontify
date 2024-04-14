package io.webcontify.backend.collections.models.dtos

data class WebContifyCollectionRelationFieldDto(
    val sourceCollectionFieldId: Long,
    val referencedCollectionFieldId: Long
)

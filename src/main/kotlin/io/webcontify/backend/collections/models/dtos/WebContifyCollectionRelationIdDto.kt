package io.webcontify.backend.collections.models.dtos

import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType

data class WebContifyCollectionRelationIdDto(
    val id: Long?,
    val sourceCollectionId: Long?,
    val leftCollectionId: Long?,
    val mappingCollectionId: Long?,
    val rightCollectionId: Long,
    val type: WebcontifyCollectionRelationType,
    val name: String,
    val displayName: String = name,
    val fields: Set<WebContifyCollectionRelationFieldDto>
)

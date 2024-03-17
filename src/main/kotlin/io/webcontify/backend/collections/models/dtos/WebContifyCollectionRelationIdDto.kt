package io.webcontify.backend.collections.models.dtos

import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType

data class WebContifyCollectionRelationIdDto(
    val sourceCollectionId: Int,
    val referencedCollectionId: Int,
    val type: WebcontifyCollectionRelationType,
    val name: String,
    val displayName: String = name,
    val fields: Set<WebContifyCollectionRelationFieldDto>
)

package io.webcontify.backend.collections.models.apis

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationFieldDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType

data class WebContifyCollectionRelationApiResponse(
    val name: String,
    val displayName: String = name,
    val type: WebcontifyCollectionRelationType,
    val sourceCollectionId: Long,
    val rightCollectionId: Long,
    val mappingCollectionId: Long?,
    val leftCollectionId: Long,
    val fields: Set<WebContifyCollectionRelationFieldDto>
)

package io.webcontify.backend.collections.models.apis

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationFieldDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType

data class WebContifyCollectionRelationApiCreateRequest(
    val name: String,
    val displayName: String = name,
    val type: WebcontifyCollectionRelationType,
    val referencedCollectionId: Int,
    val fields: Set<WebContifyCollectionRelationFieldDto>
) {}

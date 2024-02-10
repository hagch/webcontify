package io.webcontify.backend.collections.models.apis

import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType

data class WebContifyCollectionRelationApiUpdateRequest(
    val displayName: String,
    val type: WebcontifyCollectionRelationType,
    val referencedCollectionId: Int
)

package io.webcontify.backend.collections.models.apis

import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType

data class WebContifyCollectionRelationApiUpdateRequest(
    val sourceCollectionId: Int,
    val sourceCollectionColumnName: String?,
    val mappingCollectionId: Int?,
    val mappingCollectionColumnName: String?,
    val referencedCollectionId: Int,
    val referencedCollectionColumnName: String?,
    val type: WebcontifyCollectionRelationType,
    val name: String,
    val displayName: String = name
)

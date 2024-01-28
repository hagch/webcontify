package io.webcontify.backend.collections.models.dtos

import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType

data class WebContifyCollectionRelationDto(
    val sourceCollection: WebContifyCollectionDto,
    val sourceCollectionColumnName: String,
    val referencedCollection: WebContifyCollectionDto,
    val referencedCollectionColumnName: String,
    val type: WebcontifyCollectionRelationType,
    val name: String,
    val displayName: String = name
)

package io.webcontify.backend.collections.utils

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType

fun Set<WebContifyCollectionRelationDto>.switchReferences(type: WebcontifyCollectionRelationType): Set<WebContifyCollectionRelationDto> {
    return this.map {
      it.copy(
        sourceCollection = it.referencedCollection,
        sourceCollectionColumnName = it.referencedCollectionColumnName,
        referencedCollection = it.sourceCollection,
        referencedCollectionColumnName = it.sourceCollectionColumnName,
        type = type)
    }.toSet()
}

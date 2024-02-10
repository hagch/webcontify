package io.webcontify.backend.collections.models.dtos

import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType

data class WebContifyCollectionRelationDto(
    val sourceCollection: WebContifyCollectionDto,
    val referencedCollection: WebContifyCollectionDto,
    val type: WebcontifyCollectionRelationType,
    val name: String,
    val displayName: String = name,
    val fields: Set<WebContifyCollectionRelationFieldDto>
) {

  fun switchReference(type: WebcontifyCollectionRelationType): WebContifyCollectionRelationDto {
    return this.copy(
        type = type,
        sourceCollection = this.referencedCollection,
        referencedCollection = this.sourceCollection,
        fields =
            this.fields
                .map {
                  it.copy(
                      sourceCollectionColumnName = it.referencedCollectionColumnName,
                      referencedCollectionColumnName = it.sourceCollectionColumnName)
                }
                .toSet())
  }
}

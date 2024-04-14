package io.webcontify.backend.collections.models.dtos

import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType

data class WebContifyCollectionRelationDto(
    val id: Long?,
    val sourceCollection: WebContifyCollectionDto?,
    val leftCollection: WebContifyCollectionDto?,
    val mappingCollection: WebContifyCollectionDto?,
    val rightCollection: WebContifyCollectionDto?,
    val type: WebcontifyCollectionRelationType,
    val name: String,
    val displayName: String = name,
    val fields: Set<WebContifyCollectionRelationFieldDto>?
) {

  fun switchReference(type: WebcontifyCollectionRelationType): WebContifyCollectionRelationDto {
    return this.copy(
        type = type,
        rightCollection = this.leftCollection,
        leftCollection = this.rightCollection,
        fields =
            this.fields
                ?.map {
                  it.copy(
                      sourceCollectionFieldId = it.referencedCollectionFieldId,
                      referencedCollectionFieldId = it.sourceCollectionFieldId)
                }
                ?.toSet()
                ?: emptySet())
  }

  fun switchReference(): WebContifyCollectionRelationDto {
    return this.switchReference(this.type)
  }
}

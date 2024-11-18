package io.webcontify.backend.relations.models

import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType

data class RelationCollectionDto(
    val id: Long,
    val type: WebcontifyCollectionRelationType,
    val sourceCollection: RelationCollectionNameTable,
    val referencedCollection: RelationCollectionNameTable,
    val mappingCollection: RelationCollectionNameTable?
) {
  fun relationName(): String {
    return "fk_relation_${this.id}_${this.sourceCollection.id}_${this.referencedCollection.id}"
  }
}

data class RelationCollectionNameTable(val id: Long, val name: String)

package io.webcontify.backend.relations

import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType

data class RelationDto(
    val id: Long,
    val sourceCollectionMapping: CollectionRelationMapping,
    val mappingCollectionMapping: CollectionRelationMapping?,
    val referencedCollectionMapping: CollectionRelationMapping,
    val type: WebcontifyCollectionRelationType
)

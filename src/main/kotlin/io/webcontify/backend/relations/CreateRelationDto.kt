package io.webcontify.backend.relations

import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType

data class CreateRelationDto(
    val sourceCollectionMapping: CollectionRelationMapping,
    val mappingCollectionMapping: CollectionRelationMapping?,
    val referencedCollectionMapping: CollectionRelationMapping,
    val type: WebcontifyCollectionRelationType
)

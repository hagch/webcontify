package io.webcontify.backend.relations

import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType

data class CreateRelationRequest(
    val sourceCollectionMapping: CollectionRelationMapping,
    val mappingCollectionMapping: CollectionRelationMapping?,
    val referencedCollectionRelationMapping: CollectionRelationMapping,
    val type: WebcontifyCollectionRelationType
)

package io.webcontify.backend.relations.models

import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType

data class CreateRelationDto(
    val sourceCollectionMapping: CollectionRelationMapping,
    val mappingCollectionMapping: MappingCollectionRelationMapping?,
    val referencedCollectionMapping: CollectionRelationMapping,
    val type: WebcontifyCollectionRelationType
)

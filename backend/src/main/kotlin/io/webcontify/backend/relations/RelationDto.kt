package io.webcontify.backend.relations

import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType

data class RelationDto(
    val id: Long,
    val sourceCollectionMapping: MappingCollectionRelationMapping,
    val mappingCollectionMapping: MappingCollectionRelationMapping?,
    val referencedCollectionMapping: MappingCollectionRelationMapping,
    val type: WebcontifyCollectionRelationType
)

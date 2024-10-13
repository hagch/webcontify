package io.webcontify.backend.relations

import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType

data class RelationDto(
    val id: Long,
    val sourceCollectionMapping: MappingCollectionRelationMapping,
    val mappingCollectionMapping: MappingCollectionRelationMapping?,
    val referencedCollectionMapping: MappingCollectionRelationMapping,
    var mirrorFields: Set<RelationMirrorField>?,
    val type: WebcontifyCollectionRelationType
)

data class RelationMirrorField(val collectionId: Long, val fieldId: Long)

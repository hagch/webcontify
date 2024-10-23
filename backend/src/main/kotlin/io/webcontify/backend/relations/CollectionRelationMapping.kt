package io.webcontify.backend.relations

data class CollectionRelationMapping(
    val id: Long,
    val name: String,
    val fieldsMapping: Set<RelationFieldMapping>
)

data class MappingCollectionRelationMapping(
    val id: Long?,
    val name: String?,
    val fieldsMapping: Set<RelationFieldMapping>
)

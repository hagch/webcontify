package io.webcontify.backend.relations

data class CollectionRelationMapping(
    val id: Long,
    val fieldsMapping: Set<RelationFieldMapping>,
    val mirrorFields: Set<MirrorRelationFieldMapping>?
)

data class MappingCollectionRelationMapping(
    val id: Long?,
    val fieldsMapping: Set<RelationFieldMapping>
)

data class MirrorRelationFieldMapping(val name: String, val referencedFieldId: Long)

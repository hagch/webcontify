package io.webcontify.backend.relations

import io.webcontify.backend.collections.services.CollectionService
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.springframework.beans.factory.annotation.Autowired

@Mapper(componentModel = "spring")
abstract class RelationMapper {

  @Autowired private lateinit var collectionService: CollectionService

  abstract fun mapApiToDto(createRequest: CreateRelationRequest): CreateRelationDto

  @Mapping(source = "id", target = "id")
  @Mapping(source = "createDto.sourceCollectionMapping", target = "sourceCollectionMapping")
  @Mapping(source = "createDto.referencedCollectionMapping", target = "referencedCollectionMapping")
  @Mapping(source = "createDto.mappingCollectionMapping", target = "mappingCollectionMapping")
  @Mapping(source = "createDto.type", target = "type")
  @Mapping(target = "mirrorFields", ignore = true)
  abstract fun mapCreateDtoToDto(id: Long, createDto: CreateRelationDto): RelationDto

  fun toTableRelationDto(collectionId: Long, fieldIds: Set<Long>): RelationTable {
    val collection = collectionService.getById(collectionId)
    val usedFieldsInRelation =
        collection.fields?.filter { fieldIds.contains(it.id) }?.toSet()
            ?: throw RuntimeException("not allowed")
    return RelationTable(id = collectionId, collection.name, fields = usedFieldsInRelation)
  }
}

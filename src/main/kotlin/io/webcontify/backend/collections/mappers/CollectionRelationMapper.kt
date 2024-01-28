package io.webcontify.backend.collections.mappers

import io.webcontify.backend.collections.models.apis.WebContifyCollectionRelationApiCreateRequest
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationDto
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring")
interface CollectionRelationMapper {

  @Mapping(source = "sourceCollection", target = "sourceCollection")
  @Mapping(source = "referencedCollection", target = "referencedCollection")
  @Mapping(source = "relation.name", target = "name")
  @Mapping(source = "relation.displayName", target = "displayName")
  @Mapping(source = "relation.type", target = "type")
  fun mapToDto(
      relation: WebContifyCollectionRelationApiCreateRequest,
      sourceCollection: WebContifyCollectionDto,
      referencedCollection: WebContifyCollectionDto
  ): WebContifyCollectionRelationDto
}

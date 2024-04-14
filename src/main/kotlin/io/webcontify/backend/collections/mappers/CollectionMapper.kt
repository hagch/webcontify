package io.webcontify.backend.collections.mappers

import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiCreateRequest
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiResponse
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiUpdateRequest
import io.webcontify.backend.collections.models.apis.WebContifyCollectionRelationApiResponse
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationIdDto
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionFieldRecord
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionRecord
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(
    componentModel = "spring",
    uses = [CollectionFieldMapper::class, CollectionRelationMapper::class])
interface CollectionMapper {

  @Mapping(source = "collection.id", target = "id")
  @Mapping(source = "collection.name", target = "name")
  @Mapping(source = "collection.displayName", target = "displayName")
  @Mapping(source = "fields", target = "fields")
  @Mapping(source = "relations", target = "relations", defaultExpression = "java(new ArrayList())")
  fun mapToDto(
      collection: WebcontifyCollectionRecord,
      fields: Set<WebcontifyCollectionFieldRecord>,
      relations: List<WebContifyCollectionRelationIdDto>
  ): WebContifyCollectionDto

  @Mapping(source = "collection.id", target = "id")
  @Mapping(source = "collection.name", target = "name")
  @Mapping(source = "collection.displayName", target = "displayName")
  @Mapping(source = "fields", target = "fields")
  fun addFieldsToDto(
      collection: WebContifyCollectionDto,
      fields: Set<WebContifyCollectionFieldDto>
  ): WebContifyCollectionDto

  @Mapping(source = "collection.id", target = "id")
  @Mapping(source = "collection.name", target = "name")
  @Mapping(source = "collection.displayName", target = "displayName")
  @Mapping(source = "fields", target = "fields")
  fun mapCollectionToDto(
      collection: WebcontifyCollectionRecord,
      fields: Set<WebContifyCollectionFieldDto>
  ): WebContifyCollectionDto

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "relations", ignore = true)
  fun mapApiToDto(
      collectionCreateRequest: WebContifyCollectionApiCreateRequest
  ): WebContifyCollectionDto

  @Mapping(source = "id", target = "id")
  @Mapping(target = "fields", ignore = true)
  @Mapping(target = "relations", expression = "java(new ArrayList())")
  fun mapApiToDto(
      collectionCreateRequest: WebContifyCollectionApiUpdateRequest,
      id: Int
  ): WebContifyCollectionDto

  @Mapping(source = "relations", target = "relations", defaultExpression = "java(new ArrayList())")
  fun mapDtoToResponse(dto: WebContifyCollectionDto): WebContifyCollectionApiResponse

  @Mapping(source = "relations", target = "relations", defaultExpression = "java(new ArrayList())")
  fun mapDtoToResponse(
      dto: WebContifyCollectionDto,
      relations: List<WebContifyCollectionRelationApiResponse>
  ): WebContifyCollectionApiResponse
}

package io.webcontify.backend.collections.mappers

import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiCreateRequest
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiResponse
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiUpdateRequest
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldDto
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionFieldRecord
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionRecord
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring", uses = [CollectionFieldMapper::class])
interface CollectionMapper {

  @Mapping(source = "collection.id", target = "id")
  @Mapping(source = "collection.name", target = "name")
  @Mapping(source = "collection.displayName", target = "displayName")
  @Mapping(source = "fields", target = "fields")
  fun mapToDto(
      collection: WebcontifyCollectionRecord,
      fields: Set<WebcontifyCollectionFieldRecord>
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
  fun mapApiToDto(
      collectionCreateRequest: WebContifyCollectionApiCreateRequest
  ): WebContifyCollectionDto

  @Mapping(source = "id", target = "id")
  @Mapping(target = "fields", ignore = true)
  fun mapApiToDto(
      collectionCreateRequest: WebContifyCollectionApiUpdateRequest,
      id: Int
  ): WebContifyCollectionDto

  fun mapDtoToResponse(dto: WebContifyCollectionDto): WebContifyCollectionApiResponse
}

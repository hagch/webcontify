package io.webcontify.backend.collections.mappers

import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiCreateRequest
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiUpdateRequest
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionColumnRecord
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionRecord
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring", uses = [CollectionColumnMapper::class])
interface CollectionMapper {

  @Mapping(source = "collection.id", target = "id")
  @Mapping(source = "collection.name", target = "name")
  @Mapping(source = "collection.displayName", target = "displayName")
  @Mapping(source = "columns", target = "columns")
  fun mapToDto(
      collection: WebcontifyCollectionRecord,
      columns: Set<WebcontifyCollectionColumnRecord>
  ): WebContifyCollectionDto

  @Mapping(target = "columns", ignore = true)
  fun mapToDto(collection: WebcontifyCollectionRecord): WebContifyCollectionDto

  @Mapping(source = "collection.id", target = "id")
  @Mapping(source = "collection.name", target = "name")
  @Mapping(source = "collection.displayName", target = "displayName")
  @Mapping(source = "columns", target = "columns")
  fun addColumnsToDto(
      collection: WebContifyCollectionDto,
      columns: Set<WebContifyCollectionColumnDto>
  ): WebContifyCollectionDto

  @Mapping(source = "collection.id", target = "id")
  @Mapping(source = "collection.name", target = "name")
  @Mapping(source = "collection.displayName", target = "displayName")
  @Mapping(source = "columns", target = "columns")
  fun mapCollectionToDto(
      collection: WebcontifyCollectionRecord,
      columns: Set<WebContifyCollectionColumnDto>
  ): WebContifyCollectionDto

  @Mapping(target = "id", ignore = true)
  fun mapApiToDto(
      collectionCreateRequest: WebContifyCollectionApiCreateRequest
  ): WebContifyCollectionDto

  @Mapping(source = "id", target = "id")
  @Mapping(target = "columns", ignore = true)
  fun mapApiToDto(
      collectionCreateRequest: WebContifyCollectionApiUpdateRequest,
      id: Int
  ): WebContifyCollectionDto
}

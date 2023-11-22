package io.webcontify.backend.collections.mappers

import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiCreateRequest
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiUpdateRequest
import io.webcontify.backend.collections.models.apis.WebContifyCollectionColumnApiCreateRequest
import io.webcontify.backend.collections.models.apis.WebContifyCollectionColumnApiUpdateRequest
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionColumnRecord
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionRecord
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring")
interface CollectionMapper {

  @Mapping(source = "collection.id", target = "id")
  @Mapping(source = "collection.name", target = "name")
  @Mapping(source = "collection.displayName", target = "displayName")
  @Mapping(source = "columns", target = "columns")
  fun mapToDto(
      collection: WebcontifyCollectionRecord,
      columns: Set<WebcontifyCollectionColumnRecord>
  ): WebContifyCollectionDto

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

  @Mapping(source = "collectionId", target = "collectionId")
  @Mapping(source = "name", target = "name")
  @Mapping(source = "displayName", target = "displayName")
  @Mapping(source = "type", target = "type")
  @Mapping(source = "primaryKey", target = "isPrimaryKey")
  fun mapToDto(
      column: WebcontifyCollectionColumnRecord,
  ): WebContifyCollectionColumnDto

  fun mapApiToDto(
      collectionCreateRequest: WebContifyCollectionApiCreateRequest
  ): WebContifyCollectionDto

  @Mapping(source = "id", target = "id")
  fun mapApiToDto(
      collectionCreateRequest: WebContifyCollectionApiUpdateRequest,
      id: Int
  ): WebContifyCollectionDto

  @Mapping(source = "primaryKey", target = "isPrimaryKey")
  fun mapApiToDto(
      columnApiCreateRequest: WebContifyCollectionColumnApiCreateRequest
  ): WebContifyCollectionColumnDto

  @Mapping(source = "columnApiCreateRequest.primaryKey", target = "isPrimaryKey")
  @Mapping(source = "collectionId", target = "collectionId")
  fun mapApiToDto(
      columnApiCreateRequest: WebContifyCollectionColumnApiCreateRequest,
      collectionId: Int
  ): WebContifyCollectionColumnDto

  @Mapping(source = "collectionId", target = "collectionId")
  @Mapping(source = "columnApiUpdateRequest.primaryKey", target = "isPrimaryKey")
  fun mapApiToDto(
      columnApiUpdateRequest: WebContifyCollectionColumnApiUpdateRequest,
      collectionId: Int
  ): WebContifyCollectionColumnDto
}

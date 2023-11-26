package io.webcontify.backend.collections.mappers

import io.webcontify.backend.collections.models.apis.WebContifyCollectionColumnApiCreateRequest
import io.webcontify.backend.collections.models.apis.WebContifyCollectionColumnApiUpdateRequest
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionColumnRecord
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring")
interface CollectionColumnMapper {

  @Mapping(source = "collectionId", target = "collectionId")
  @Mapping(source = "name", target = "name")
  @Mapping(source = "displayName", target = "displayName")
  @Mapping(source = "type", target = "type")
  @Mapping(source = "primaryKey", target = "isPrimaryKey")
  fun mapToDto(
      column: WebcontifyCollectionColumnRecord,
  ): WebContifyCollectionColumnDto

  @Mapping(source = "primaryKey", target = "isPrimaryKey")
  @Mapping(target = "collectionId", ignore = true)
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

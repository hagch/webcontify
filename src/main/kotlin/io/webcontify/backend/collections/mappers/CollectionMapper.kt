package io.webcontify.backend.collections.mappers

import io.webcontify.backend.collections.models.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.models.WebContifyCollectionDto
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
}

package io.webcontify.backend.converters

import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionColumnRecord
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionRecord
import io.webcontify.backend.models.WebContifyCollectionColumnDto
import io.webcontify.backend.models.WebContifyCollectionDto
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring")
interface CollectionConverter {

  @Mapping(source = "collection.id", target = "id")
  @Mapping(source = "collection.name", target = "name")
  @Mapping(source = "collection.displayName", target = "displayName")
  @Mapping(source = "columns", target = "columns")
  fun convertToDto(
      collection: WebcontifyCollectionRecord,
      columns: Set<WebcontifyCollectionColumnRecord>
  ): WebContifyCollectionDto

  @Mapping(source = "collection.id", target = "id")
  @Mapping(source = "collection.name", target = "name")
  @Mapping(source = "collection.displayName", target = "displayName")
  @Mapping(source = "columns", target = "columns")
  fun convertCollectionToDto(
      collection: WebcontifyCollectionRecord,
      columns: Set<WebContifyCollectionColumnDto>
  ): WebContifyCollectionDto

  @Mapping(source = "collectionId", target = "collectionId")
  @Mapping(source = "name", target = "name")
  @Mapping(source = "displayName", target = "displayName")
  @Mapping(source = "type", target = "type")
  @Mapping(source = "primaryKey", target = "isPrimaryKey")
  fun convertToDto(
      column: WebcontifyCollectionColumnRecord,
  ): WebContifyCollectionColumnDto
}

package io.webcontify.backend.converters

import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionColumnRecord
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionRecord
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
}

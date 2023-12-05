package io.webcontify.backend.collections.mappers

import io.webcontify.backend.collections.models.apis.WebContifyCollectionColumnApiCreateRequest
import io.webcontify.backend.collections.models.apis.WebContifyCollectionColumnApiUpdateRequest
import io.webcontify.backend.collections.models.dtos.*
import io.webcontify.backend.collections.services.column.handler.ColumnHandlerStrategy
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionColumnRecord
import org.jooq.JSONB
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.Named
import org.springframework.beans.factory.annotation.Autowired

@Mapper(componentModel = "spring")
abstract class CollectionColumnMapper {

  @Autowired private lateinit var handler: ColumnHandlerStrategy

  @Mapping(source = "collectionId", target = "collectionId")
  @Mapping(source = "name", target = "name")
  @Mapping(source = "displayName", target = "displayName")
  @Mapping(source = "type", target = "type")
  @Mapping(source = "primaryKey", target = "isPrimaryKey")
  @Mapping(source = "column", target = "configuration", qualifiedByName = ["mapConfiguration"])
  abstract fun mapToDto(
      column: WebcontifyCollectionColumnRecord,
  ): WebContifyCollectionColumnDto

  @Mapping(source = "primaryKey", target = "isPrimaryKey")
  @Mapping(target = "collectionId", ignore = true)
  abstract fun mapApiToDto(
      columnApiCreateRequest: WebContifyCollectionColumnApiCreateRequest
  ): WebContifyCollectionColumnDto

  @Mapping(source = "columnApiCreateRequest.primaryKey", target = "isPrimaryKey")
  @Mapping(source = "collectionId", target = "collectionId")
  abstract fun mapApiToDto(
      columnApiCreateRequest: WebContifyCollectionColumnApiCreateRequest,
      collectionId: Int
  ): WebContifyCollectionColumnDto

  @Mapping(source = "collectionId", target = "collectionId")
  @Mapping(source = "columnApiUpdateRequest.primaryKey", target = "isPrimaryKey")
  abstract fun mapApiToDto(
      columnApiUpdateRequest: WebContifyCollectionColumnApiUpdateRequest,
      collectionId: Int
  ): WebContifyCollectionColumnDto

  @Named("mapConfiguration")
  fun mapConfiguration(
      column: WebcontifyCollectionColumnRecord
  ): WebContifyCollectionColumnConfigurationDto? {
    return column.configuration?.let {
      return handler.mapJSONBToConfiguration(
          WebContifyCollectionColumnDto(
              column.collectionId,
              column.name!!,
              column.displayName!!,
              column.type!!,
              column.isPrimaryKey!!,
              null),
          column.configuration)
    }
  }

  fun mapConfigurationToPersistence(column: WebContifyCollectionColumnDto): JSONB? {
    return handler.mapConfigurationToJSONB(column)
  }
}

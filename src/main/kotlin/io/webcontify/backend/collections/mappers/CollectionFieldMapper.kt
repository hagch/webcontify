package io.webcontify.backend.collections.mappers

import io.webcontify.backend.collections.models.apis.WebContifyCollectionFieldApiCreateRequest
import io.webcontify.backend.collections.models.apis.WebContifyCollectionFieldApiUpdateRequest
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldConfigurationDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldDto
import io.webcontify.backend.collections.services.field.handler.FieldHandlerStrategy
import io.webcontify.backend.collections.utils.snakeToCamelCase
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionFieldRecord
import org.jooq.JSONB
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.Named
import org.springframework.beans.factory.annotation.Autowired

@Mapper(componentModel = "spring")
abstract class CollectionFieldMapper {

  @Autowired private lateinit var handler: FieldHandlerStrategy

  @Mapping(source = "collectionId", target = "collectionId")
  @Mapping(source = "name", target = "name", qualifiedByName = ["mapFieldNameSnakeToCamelCase"])
  @Mapping(source = "displayName", target = "displayName")
  @Mapping(source = "type", target = "type")
  @Mapping(source = "primaryKey", target = "isPrimaryKey")
  @Mapping(source = "field", target = "configuration", qualifiedByName = ["mapConfiguration"])
  abstract fun mapToDto(
      field: WebcontifyCollectionFieldRecord,
  ): WebContifyCollectionFieldDto

  @Mapping(source = "primaryKey", target = "isPrimaryKey")
  @Mapping(target = "collectionId", ignore = true)
  @Mapping(target = "id", ignore = true)
  abstract fun mapApiToDto(
      fieldApiCreateRequest: WebContifyCollectionFieldApiCreateRequest
  ): WebContifyCollectionFieldDto

  @Mapping(source = "fieldApiCreateRequest.primaryKey", target = "isPrimaryKey")
  @Mapping(source = "collectionId", target = "collectionId")
  @Mapping(target = "id", ignore = true)
  abstract fun mapApiToDto(
      fieldApiCreateRequest: WebContifyCollectionFieldApiCreateRequest,
      collectionId: Long
  ): WebContifyCollectionFieldDto

  @Mapping(source = "collectionId", target = "collectionId")
  @Mapping(source = "fieldApiUpdateRequest.primaryKey", target = "isPrimaryKey")
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "configuration", ignore = true)
  abstract fun mapApiToDto(
      fieldApiUpdateRequest: WebContifyCollectionFieldApiUpdateRequest,
      collectionId: Long
  ): WebContifyCollectionFieldDto

  @Named("mapConfiguration")
  fun mapConfiguration(
      field: WebcontifyCollectionFieldRecord
  ): WebContifyCollectionFieldConfigurationDto<Any?>? {
    return field.configuration?.let {
      return handler.mapJSONBToConfiguration(
          WebContifyCollectionFieldDto(
              field.id,
              field.collectionId,
              field.name!!,
              field.displayName!!,
              field.type!!,
              field.isPrimaryKey!!,
              null),
          field.configuration)
    }
  }

  @Named("mapFieldNameSnakeToCamelCase")
  fun mapFieldNameSnakeToCamelCase(name: String): String {
    return name.snakeToCamelCase()
  }

  fun mapConfigurationToPersistence(field: WebContifyCollectionFieldDto): JSONB? {
    return handler.mapConfigurationToJSONB(field)
  }
}

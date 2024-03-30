package io.webcontify.backend.collections.mappers

import io.webcontify.backend.collections.models.apis.WebContifyCollectionRelationApiCreateRequest
import io.webcontify.backend.collections.models.apis.WebContifyCollectionRelationApiResponse
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationFieldDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationIdDto
import io.webcontify.backend.jooq.tables.WebcontifyCollectionRelationField
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionRelationFieldRecord
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionRelationRecord
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring")
interface CollectionRelationMapper {

  @Mapping(source = "sourceCollection", target = "sourceCollection")
  @Mapping(source = "referencedCollection", target = "referencedCollection")
  @Mapping(source = "relation.name", target = "name")
  @Mapping(source = "relation.displayName", target = "displayName")
  @Mapping(source = "relation.type", target = "type")
  @Mapping(source = "relation.fields", target = "fields")
  fun mapToDto(
      relation: WebContifyCollectionRelationApiCreateRequest,
      sourceCollection: WebContifyCollectionDto,
      referencedCollection: WebContifyCollectionDto
  ): WebContifyCollectionRelationDto

  @Mapping(source = "sourceCollection.id", target = "sourceCollectionId")
  @Mapping(source = "referencedCollection.id", target = "referencedCollectionId")
  fun mapToResponse(dto: WebContifyCollectionRelationDto): WebContifyCollectionRelationApiResponse

  fun fieldEntityToDto(
      field: WebcontifyCollectionRelationField
  ): WebContifyCollectionRelationFieldDto

  @Mapping(source = "fields", target = "fields")
  @Mapping(source = "dto.type", target = "type")
  fun entityToDto(
      dto: WebcontifyCollectionRelationRecord,
      fields: List<WebcontifyCollectionRelationFieldRecord>
  ): WebContifyCollectionRelationDto

  @Mapping(source = "fields", target = "fields")
  @Mapping(source = "dto.type", target = "type")
  fun entityToIdDto(
      dto: WebcontifyCollectionRelationRecord,
      fields: List<WebcontifyCollectionRelationFieldRecord>
  ): WebContifyCollectionRelationIdDto
}

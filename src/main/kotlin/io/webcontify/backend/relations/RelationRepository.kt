package io.webcontify.backend.relations

import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionRelationFieldRecord
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_RELATION
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_RELATION_FIELD
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class RelationRepository(
    private val dslContext: DSLContext,
    private val relationMapper: RelationMapper
) {

  @Transactional(readOnly = false)
  fun create(createRelationDto: CreateRelationDto): RelationDto {
    val relation = dslContext.newRecord(WEBCONTIFY_COLLECTION_RELATION)
    relation.type = createRelationDto.type
    relation.mappingCollectionId = createRelationDto.mappingCollectionMapping?.id
    relation.referencedCollectionId = createRelationDto.referencedCollectionMapping.id
    relation.sourceCollectionId = createRelationDto.sourceCollectionMapping.id
    relation.insert()
    val mappingFieldRecords =
        createRelationDto.mappingCollectionMapping?.fieldsMapping?.let {
          createFieldRecords(relation.id!!, it)
        }
            ?: emptyList()
    val sourceFieldRecords =
        createFieldRecords(relation.id!!, createRelationDto.sourceCollectionMapping.fieldsMapping)
    val referencedFieldRecords =
        createFieldRecords(
            relation.id!!, createRelationDto.referencedCollectionMapping.fieldsMapping)
    dslContext.batchInsert(mappingFieldRecords + sourceFieldRecords + referencedFieldRecords)
    return relationMapper.mapCreateDtoToDto(relation.id!!, createRelationDto)
  }

  fun delete(dto: RelationDto) {
    dslContext
        .deleteFrom(WEBCONTIFY_COLLECTION_RELATION)
        .where(WEBCONTIFY_COLLECTION_RELATION.ID.eq(dto.id))
        .execute()
  }

  private fun createFieldRecords(
      relationId: Long,
      fields: Set<RelationFieldMapping>
  ): List<WebcontifyCollectionRelationFieldRecord> {
    return fields.map {
      val field = dslContext.newRecord(WEBCONTIFY_COLLECTION_RELATION_FIELD)
      field.relationId = relationId
      field.sourceFieldId = it.sourceFieldId
      field.referencedFieldId = it.referencedFieldId
      return@map field
    }
  }
}

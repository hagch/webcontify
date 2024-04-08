package io.webcontify.backend.relations

import io.webcontify.backend.jooq.tables.WebcontifyCollection
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionRelationFieldRecord
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_RELATION
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_RELATION_FIELD
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class RelationRepository(
    private val dslContext: DSLContext,
    private val relationMapper: RelationMapper
) {

  private val sourceCollectionTable = WEBCONTIFY_COLLECTION.`as`("sourceCollection")
  private val referencedCollectionTable = WEBCONTIFY_COLLECTION.`as`("referencedCollection")
  private val mappingCollectionTable = WEBCONTIFY_COLLECTION.`as`("mappingCollectionTable")

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
    dslContext
        .batchInsert(mappingFieldRecords + sourceFieldRecords + referencedFieldRecords)
        .execute()
    return relationMapper.mapCreateDtoToDto(relation.id!!, createRelationDto)
  }

  fun findById(id: Long): RelationCollectionDto {
    return dslContext
        .select()
        .from(WEBCONTIFY_COLLECTION_RELATION)
        .innerJoin(sourceCollectionTable)
        .on(WEBCONTIFY_COLLECTION_RELATION.SOURCE_COLLECTION_ID.eq(sourceCollectionTable.ID))
        .innerJoin(referencedCollectionTable)
        .on(
            WEBCONTIFY_COLLECTION_RELATION.REFERENCED_COLLECTION_ID.eq(
                referencedCollectionTable.ID))
        .leftJoin(mappingCollectionTable)
        .on(WEBCONTIFY_COLLECTION_RELATION.MAPPING_COLLECTION_ID.eq(mappingCollectionTable.ID))
        .where(WEBCONTIFY_COLLECTION_RELATION.ID.eq(id))
        .fetchOne()
        ?.map { mapToRelationCollectionDto(it) }
        ?: throw RuntimeException("Relation not found")
  }

  private fun mapToRelationCollectionDto(record: Record): RelationCollectionDto {
    val sourceCollection = recordToRelationCollectionNameTable(record, sourceCollectionTable)!!
    val referencedCollection =
        recordToRelationCollectionNameTable(record, referencedCollectionTable)!!
    val mappingCollection = recordToRelationCollectionNameTable(record, mappingCollectionTable)
    return RelationCollectionDto(
        id = record.getValue(WEBCONTIFY_COLLECTION_RELATION.ID)!!,
        sourceCollection = sourceCollection,
        referencedCollection = referencedCollection,
        mappingCollection = mappingCollection,
        type = record.getValue(WEBCONTIFY_COLLECTION_RELATION.TYPE)!!)
  }

  private fun recordToRelationCollectionNameTable(
      record: Record,
      table: WebcontifyCollection
  ): RelationCollectionNameTable? {
    val id = record.getValue(table.ID) ?: return null
    return RelationCollectionNameTable(id = id, name = record.getValue(table.NAME)!!)
  }

  fun delete(id: Long) {
    dslContext
        .deleteFrom(WEBCONTIFY_COLLECTION_RELATION)
        .where(WEBCONTIFY_COLLECTION_RELATION.ID.eq(id))
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

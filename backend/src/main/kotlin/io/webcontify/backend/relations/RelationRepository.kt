package io.webcontify.backend.relations

import io.webcontify.backend.collections.mappers.CollectionFieldMapper
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldConfigurationDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType
import io.webcontify.backend.jooq.tables.WebcontifyCollection
import io.webcontify.backend.jooq.tables.WebcontifyCollectionField
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionRelationFieldRecord
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionRelationRecord
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_FIELD
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_RELATION
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_RELATION_FIELD
import io.webcontify.backend.relations.mappers.RelationMapper
import io.webcontify.backend.relations.models.*
import java.util.stream.Collectors.*
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class RelationRepository(
    private val dslContext: DSLContext,
    private val relationMapper: RelationMapper,
    private val collectionFieldMapper: CollectionFieldMapper
) {

  private val sourceCollectionTable = WEBCONTIFY_COLLECTION.`as`("sourceCollection")
  private val referencedCollectionTable = WEBCONTIFY_COLLECTION.`as`("referencedCollection")
  private val mappingCollectionTable = WEBCONTIFY_COLLECTION.`as`("mappingCollectionTable")
  private val sourceCollectionFieldTable = WEBCONTIFY_COLLECTION_FIELD.`as`("sourceCollectionField")
  private val referencedCollectionFieldTable =
      WEBCONTIFY_COLLECTION_FIELD.`as`("referencedCollectionField")
  private val mappingCollectionFieldTable =
      WEBCONTIFY_COLLECTION_FIELD.`as`("mappingCollectionTableField")

  @Transactional(readOnly = false)
  fun create(createRelationDto: CreateRelationDto): RelationDto {
    val relation = dslContext.newRecord(WEBCONTIFY_COLLECTION_RELATION)
    relation.type = createRelationDto.type
    relation.mappingCollectionId = createRelationDto.mappingCollectionMapping?.id
    relation.mappingRelationName = createRelationDto.mappingCollectionMapping?.name
    relation.referencedCollectionId = createRelationDto.referencedCollectionMapping.id
    relation.referencedRelationName = createRelationDto.referencedCollectionMapping.name
    relation.sourceRelationName = createRelationDto.sourceCollectionMapping.name
    relation.sourceCollectionId = createRelationDto.sourceCollectionMapping.id
    relation.insert()
    val mappingFieldRecords =
        createRelationDto.mappingCollectionMapping?.fieldsMapping?.let {
          createFieldRecords(relation.id!!, it)
        } ?: emptyList()
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
        ?.map { mapToRelationCollectionDto(it) } ?: throw RuntimeException("Relation not found")
  }

  fun getInfoByIds(ids: List<Long>): List<RelationInfoDto> {
    val result: Map<WebcontifyCollectionRelationRecord, List<Record>> =
        dslContext
            .select()
            .from(WEBCONTIFY_COLLECTION_RELATION)
            .innerJoin(sourceCollectionTable)
            .on(WEBCONTIFY_COLLECTION_RELATION.SOURCE_COLLECTION_ID.eq(sourceCollectionTable.ID))
            .innerJoin(sourceCollectionFieldTable)
            .on(sourceCollectionFieldTable.COLLECTION_ID.eq(sourceCollectionTable.ID))
            .innerJoin(referencedCollectionTable)
            .on(
                WEBCONTIFY_COLLECTION_RELATION.REFERENCED_COLLECTION_ID.eq(
                    referencedCollectionTable.ID))
            .innerJoin(referencedCollectionFieldTable)
            .on(referencedCollectionFieldTable.COLLECTION_ID.eq(referencedCollectionTable.ID))
            .leftJoin(mappingCollectionTable)
            .on(WEBCONTIFY_COLLECTION_RELATION.MAPPING_COLLECTION_ID.eq(mappingCollectionTable.ID))
            .leftJoin(mappingCollectionFieldTable)
            .on(mappingCollectionFieldTable.COLLECTION_ID.eq(mappingCollectionTable.ID))
            .innerJoin(WEBCONTIFY_COLLECTION_RELATION_FIELD)
            .on(WEBCONTIFY_COLLECTION_RELATION_FIELD.RELATION_ID.`in`(ids))
            .where(WEBCONTIFY_COLLECTION_RELATION.ID.`in`(ids))
            .collect(
                groupingBy(
                    { r -> r.into(WebcontifyCollectionRelationRecord::class.java) },
                    mapping({ r -> r }, toList())))
    return mapToRelationInfos(result)
  }

  fun isCollectionUsedAsMappingTableInRelation(collectionId: Long): Boolean {
    return dslContext.fetchExists(
        dslContext
            .select()
            .from(WEBCONTIFY_COLLECTION_RELATION)
            .where(WEBCONTIFY_COLLECTION_RELATION.MAPPING_COLLECTION_ID.eq(collectionId)))
  }

  private fun mapToRelationInfos(
      record: Map<WebcontifyCollectionRelationRecord, List<Record>>
  ): List<RelationInfoDto> {
    return record.entries.map { mapToRelationInfo(it) }
  }

  private fun mapToRelationInfo(
      entry: Map.Entry<WebcontifyCollectionRelationRecord, List<Record>>
  ): RelationInfoDto {
    val sourceCollection =
        recordToWebContifyCollection(entry, sourceCollectionTable, sourceCollectionFieldTable)!!
    val referencedCollection =
        recordToWebContifyCollection(
            entry, referencedCollectionTable, referencedCollectionFieldTable)!!
    val mappingCollection =
        recordToWebContifyCollection(entry, mappingCollectionTable, mappingCollectionFieldTable)
    val fieldMappings =
        entry.value
            .map {
              val sourceFieldId = it.getValue(WEBCONTIFY_COLLECTION_RELATION_FIELD.SOURCE_FIELD_ID)
              val referencedFieldId =
                  it.getValue(WEBCONTIFY_COLLECTION_RELATION_FIELD.REFERENCED_FIELD_ID)
              if (sourceFieldId == null || referencedFieldId == null) return@map null
              return@map RelationFieldMapping(sourceFieldId, referencedFieldId)
            }
            .filterNotNull()
    return RelationInfoDto(
        id = entry.key.getValue(WEBCONTIFY_COLLECTION_RELATION.ID)!!,
        type = entry.key.getValue(WEBCONTIFY_COLLECTION_RELATION.TYPE)!!,
        sourceCollectionRelationName =
            entry.key.getValue(WEBCONTIFY_COLLECTION_RELATION.SOURCE_RELATION_NAME)!!,
        sourceCollection = sourceCollection,
        referencedCollection = referencedCollection,
        referencedCollectionRelationName =
            entry.key.getValue(WEBCONTIFY_COLLECTION_RELATION.REFERENCED_RELATION_NAME)!!,
        mappingCollection = mappingCollection,
        mappingCollectionRelationName =
            entry.key.getValue(WEBCONTIFY_COLLECTION_RELATION.MAPPING_RELATION_NAME),
        fieldMapping = fieldMappings.toSet())
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

  private fun recordToWebContifyCollection(
      record: Map.Entry<Record, List<Record>>,
      table: WebcontifyCollection,
      fieldTable: WebcontifyCollectionField
  ): WebContifyCollectionDto? {
    val first = record.value.firstOrNull()
    val id = first!!.getValue(table.ID) ?: return null
    return WebContifyCollectionDto(
        id = id,
        name = first.getValue(table.NAME)!!,
        displayName = first.getValue(table.DISPLAY_NAME)!!,
        fields =
            record.value
                .asSequence()
                .map {
                  if (it.getValue(fieldTable.ID) == null) return@map null
                  return@map WebContifyCollectionFieldDto(
                      collectionId = id,
                      id = it.getValue(fieldTable.ID)!!,
                      name = it.getValue(fieldTable.NAME)!!,
                      type = it.getValue(fieldTable.TYPE)!!,
                      displayName = it.getValue(fieldTable.DISPLAY_NAME)!!,
                      isPrimaryKey = it.getValue(fieldTable.IS_PRIMARY_KEY)!!,
                      configuration =
                          collectionFieldMapper.mapConfiguration(it.into(fieldTable.recordType))
                              as WebContifyCollectionFieldConfigurationDto<Any>?)
                }
                .filterNotNull()
                .distinctBy { it.name }
                .toSet()
                .toList())
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

data class RelationInfoDto(
    val id: Long,
    val type: WebcontifyCollectionRelationType,
    val sourceCollectionRelationName: String,
    val sourceCollection: WebContifyCollectionDto,
    val referencedCollection: WebContifyCollectionDto,
    val referencedCollectionRelationName: String,
    val mappingCollection: WebContifyCollectionDto?,
    val mappingCollectionRelationName: String?,
    val fieldMapping: Set<RelationFieldMapping>
)

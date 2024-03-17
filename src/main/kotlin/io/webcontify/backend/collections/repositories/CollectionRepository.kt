package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.mappers.CollectionMapper
import io.webcontify.backend.collections.mappers.CollectionRelationMapper
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionColumnRecord
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionRecord
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionRelationFieldRecord
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionRelationRecord
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_COLUMN
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_RELATION
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_RELATION_FIELD
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SelectConnectByStep
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class CollectionRepository(
    val dslContext: DSLContext,
    val mapper: CollectionMapper,
    val relationMapper: CollectionRelationMapper
) {

  @Transactional(readOnly = true)
  fun getById(id: Int?): WebContifyCollectionDto {
    val collection =
        dslContext
            .select()
            .from(WEBCONTIFY_COLLECTION)
            .leftJoin(WEBCONTIFY_COLLECTION_COLUMN)
            .onKey()
            .leftJoin(WEBCONTIFY_COLLECTION_RELATION)
            .on(WEBCONTIFY_COLLECTION.ID.eq(WEBCONTIFY_COLLECTION_RELATION.SOURCE_COLLECTION_ID))
            .leftJoin(WEBCONTIFY_COLLECTION_RELATION_FIELD)
            .on(
                WEBCONTIFY_COLLECTION.ID.eq(
                    WEBCONTIFY_COLLECTION_RELATION_FIELD.SOURCE_COLLECTION_ID))
            .where(WEBCONTIFY_COLLECTION.ID.eq(id))
            .asWebcontifyCollectionDto(mapper)
    return collection ?: throw NotFoundException(ErrorCode.COLLECTION_NOT_FOUND, id.toString())
  }

  @Transactional(readOnly = true)
  fun getAll(): Set<WebContifyCollectionDto> {
    return dslContext
        .select()
        .from(WEBCONTIFY_COLLECTION)
        .leftJoin(WEBCONTIFY_COLLECTION_COLUMN)
        .onKey()
        .leftJoin(WEBCONTIFY_COLLECTION_RELATION)
        .on(WEBCONTIFY_COLLECTION.ID.eq(WEBCONTIFY_COLLECTION_RELATION.SOURCE_COLLECTION_ID))
        .leftJoin(WEBCONTIFY_COLLECTION_RELATION_FIELD)
        .on(WEBCONTIFY_COLLECTION.ID.eq(WEBCONTIFY_COLLECTION_RELATION_FIELD.SOURCE_COLLECTION_ID))
        .asWebcontifyCollectionDtoSet(mapper)
  }

  @Transactional
  fun deleteById(id: Int?) {
    try {
      dslContext.deleteFrom(WEBCONTIFY_COLLECTION).where(WEBCONTIFY_COLLECTION.ID.eq(id)).execute()
    } catch (exception: DataIntegrityViolationException) {
      throw UnprocessableContentException(ErrorCode.CANNOT_DELETE_COLLECTION, id.toString())
    }
  }

  @Transactional
  fun update(record: WebContifyCollectionDto): WebContifyCollectionDto {
    return dslContext.newRecord(WEBCONTIFY_COLLECTION).let { updateAbleRecord ->
      updateAbleRecord.displayName = record.displayName
      updateAbleRecord.name = record.name
      updateAbleRecord.id = record.id
      updateAbleRecord.update().let {
        if (it == 0) {
          throw NotFoundException(ErrorCode.COLLECTION_NOT_FOUND, updateAbleRecord.id.toString())
        }
      }

      return@let mapper.mapToDto(updateAbleRecord)
    }
  }

  @Transactional
  fun create(record: WebContifyCollectionDto): WebContifyCollectionDto {
    val collection =
        dslContext.newRecord(WEBCONTIFY_COLLECTION).apply {
          this.displayName = record.displayName
          this.name = record.name
          try {
            this.insert()
          } catch (e: DuplicateKeyException) {
            throw AlreadyExistsException(
                ErrorCode.COLLECTION_WITH_NAME_ALREADY_EXISTS, this.name.toString())
          }
        }
    return mapper.mapCollectionToDto(collection, setOf())
  }

  private fun SelectConnectByStep<Record>.collectToCollectionMap():
      Map<
          WebcontifyCollectionRecord,
          Pair<
              Set<WebcontifyCollectionColumnRecord>,
              Map<
                  WebcontifyCollectionRelationRecord,
                  List<WebcontifyCollectionRelationFieldRecord>>>> {
    return this.groupBy { r -> r.into(WebcontifyCollectionRecord::class.java) }
        .mapValues {
          Pair(
              it.value
                  .filter { r -> r.get(WEBCONTIFY_COLLECTION_COLUMN.COLLECTION_ID) != null }
                  .map { r -> r.into(WebcontifyCollectionColumnRecord::class.java) }
                  .toSet(),
              it.value
                  .filter { r ->
                    r.get(WEBCONTIFY_COLLECTION_RELATION.SOURCE_COLLECTION_ID) != null
                  }
                  .groupBy(
                      { r -> r.into(WebcontifyCollectionRelationRecord::class.java) },
                      { r -> r.into(WebcontifyCollectionRelationFieldRecord::class.java) })
                  .toMap())
        }
  }

  private fun SelectConnectByStep<Record>.asWebcontifyCollectionDto(
      converter: CollectionMapper
  ): WebContifyCollectionDto? {
    return this.collectToCollectionMap().firstNotNullOfOrNull { (collection, references) ->
      val relations =
          references.second.entries.map { relationMapper.entityToIdDto(it.key, it.value) }.toList()
      converter.mapToDto(collection, references.first, relations)
    }
  }

  private fun SelectConnectByStep<Record>.asWebcontifyCollectionDtoSet(
      converter: CollectionMapper
  ): Set<WebContifyCollectionDto> {
    return this.collectToCollectionMap()
        .map { (collection, references) ->
          val relations =
              references.second.entries
                  .map { relationMapper.entityToIdDto(it.key, it.value) }
                  .toList()
          converter.mapToDto(collection, references.first, relations)
        }
        .toHashSet()
  }
}

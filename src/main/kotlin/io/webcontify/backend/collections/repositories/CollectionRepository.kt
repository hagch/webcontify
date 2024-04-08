package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.mappers.CollectionMapper
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.jooq.tables.records.*
import io.webcontify.backend.jooq.tables.references.*
import java.util.stream.Collectors
import java.util.stream.Collectors.filtering
import java.util.stream.Collectors.mapping
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SelectConnectByStep
import org.jooq.SelectOnConditionStep
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class CollectionRepository(val dslContext: DSLContext, val mapper: CollectionMapper) {

  private fun getCollectionQuery(): SelectOnConditionStep<Record> {
    return dslContext
        .select()
        .from(WEBCONTIFY_COLLECTION)
        .leftJoin(WEBCONTIFY_COLLECTION_FIELD)
        .onKey()
  }

  @Transactional(readOnly = true)
  fun getById(id: Long?): WebContifyCollectionDto {
    val collection =
        getCollectionQuery()
            .where(WEBCONTIFY_COLLECTION.ID.eq(id))
            .asWebcontifyCollectionDto(mapper)
    return collection ?: throw NotFoundException(ErrorCode.COLLECTION_NOT_FOUND, id.toString())
  }

  @Transactional(readOnly = true)
  fun getAll(): Set<WebContifyCollectionDto> {
    return getCollectionQuery().asWebcontifyCollectionDtoSet(mapper)
  }

  @Transactional
  fun deleteById(id: Long?) {
    try {
      dslContext.deleteFrom(WEBCONTIFY_COLLECTION).where(WEBCONTIFY_COLLECTION.ID.eq(id)).execute()
    } catch (exception: DataIntegrityViolationException) {
      throw UnprocessableContentException(ErrorCode.CANNOT_DELETE_COLLECTION, id.toString())
    }
  }

  @Transactional
  fun update(collection: WebContifyCollectionDto): WebContifyCollectionDto {
    val updateAbleRecord = dslContext.newRecord(WEBCONTIFY_COLLECTION)
    updateAbleRecord.displayName = collection.displayName
    updateAbleRecord.name = collection.name
    updateAbleRecord.id = collection.id
    try {
      updateAbleRecord.update().let {
        if (it == 0) {
          throw NotFoundException(ErrorCode.COLLECTION_NOT_FOUND, updateAbleRecord.id.toString())
        }
      }
    } catch (e: DuplicateKeyException) {
      throw AlreadyExistsException(
          ErrorCode.COLLECTION_WITH_NAME_ALREADY_EXISTS, updateAbleRecord.id.toString())
    }
    return collection
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
      Map<WebcontifyCollectionRecord, Set<WebcontifyCollectionFieldRecord>> {
    return this.collect(
        Collectors.groupingBy(
            { r -> r.into(WebcontifyCollectionRecord::class.java) },
            filtering(
                { r -> r.get(WEBCONTIFY_COLLECTION_FIELD.COLLECTION_ID) != null },
                mapping(
                    { r -> r.into(WebcontifyCollectionFieldRecord::class.java) },
                    Collectors.toSet()))))
  }

  private fun SelectConnectByStep<Record>.asWebcontifyCollectionDto(
      converter: CollectionMapper
  ): WebContifyCollectionDto? {
    return this.collectToCollectionMap().firstNotNullOfOrNull { (collection, columns) ->
      converter.mapToDto(collection, columns)
    }
  }

  private fun SelectConnectByStep<Record>.asWebcontifyCollectionDtoSet(
      converter: CollectionMapper
  ): Set<WebContifyCollectionDto> {
    return this.collectToCollectionMap()
        .map { (collection, columns) -> converter.mapToDto(collection, columns) }
        .toHashSet()
  }
}

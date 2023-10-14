package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.mappers.CollectionMapper
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionColumnRecord
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionRecord
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_COLUMN
import java.util.stream.Collectors
import java.util.stream.Collectors.*
import org.jooq.*
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Repository

@Repository
class CollectionRepository(
    val dslContext: DSLContext,
    val mapper: CollectionMapper,
    val columnRepository: CollectionColumnRepository
) {

  fun getById(id: Int?): WebContifyCollectionDto {
    val collection =
        dslContext
            .select()
            .from(WEBCONTIFY_COLLECTION)
            .leftJoin(WEBCONTIFY_COLLECTION_COLUMN)
            .onKey()
            .where(WEBCONTIFY_COLLECTION.ID.eq(id))
            .asWebcontifyCollectionDto(mapper)
    return collection ?: throw RuntimeException()
  }

  fun getAll(): Set<WebContifyCollectionDto> {
    return dslContext
        .select()
        .from(WEBCONTIFY_COLLECTION)
        .leftJoin(WEBCONTIFY_COLLECTION_COLUMN)
        .onKey()
        .asWebcontifyCollectionDtoSet(mapper)
  }

  fun deleteById(id: Int?) {
    columnRepository.deleteAllForCollection(id)
    dslContext.deleteFrom(WEBCONTIFY_COLLECTION).where(WEBCONTIFY_COLLECTION.ID.eq(id)).execute()
  }

  fun update(record: WebContifyCollectionDto): WebContifyCollectionDto {
    return dslContext.newRecord(WEBCONTIFY_COLLECTION).let { updateAbleRecord ->
      updateAbleRecord.displayName = record.displayName
      updateAbleRecord.name = record.name
      updateAbleRecord.id = record.id
      updateAbleRecord.update().let {
        if (it == 0) {
          throw RuntimeException()
        }
      }

      return@let mapper.mapCollectionToDto(
          updateAbleRecord, columnRepository.getAllForCollection(updateAbleRecord.id))
    }
  }

  fun create(record: WebContifyCollectionDto): WebContifyCollectionDto {
    try {
      val collection =
          dslContext.newRecord(WEBCONTIFY_COLLECTION).apply {
            this.displayName = record.displayName
            this.name = record.name
            this.insert()
          }
      return mapper.mapCollectionToDto(collection, setOf())
    } catch (e: DuplicateKeyException) {
      // NAME already exists
      throw RuntimeException()
    }
  }

  private fun SelectConnectByStep<Record>.collectToCollectionMap():
      Map<WebcontifyCollectionRecord, Set<WebcontifyCollectionColumnRecord>> {
    return this.collect(
        groupingBy(
            { r -> r.into(WebcontifyCollectionRecord::class.java) },
            filtering(
                { r -> r.get(WEBCONTIFY_COLLECTION_COLUMN.COLLECTION_ID) != null },
                mapping(
                    { r -> r.into(WebcontifyCollectionColumnRecord::class.java) },
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

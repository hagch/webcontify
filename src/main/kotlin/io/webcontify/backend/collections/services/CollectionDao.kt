package io.webcontify.backend.collections.services

import io.webcontify.backend.converters.CollectionConverter
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionColumnRecord
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionRecord
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_COLUMN
import io.webcontify.backend.models.WebContifyCollectionDto
import java.util.stream.Collectors
import java.util.stream.Collectors.*
import org.jooq.*
import org.springframework.stereotype.Component

@Component
class CollectionDao(val dslContext: DSLContext, val converter: CollectionConverter) {

  fun getById(id: Int?): WebContifyCollectionDto {
    val collection =
        dslContext
            .select()
            .from(WEBCONTIFY_COLLECTION)
            .leftJoin(WEBCONTIFY_COLLECTION_COLUMN)
            .onKey()
            .where(WEBCONTIFY_COLLECTION.ID.eq(id))
            .asWebcontifyCollectionDto(converter)
    return collection ?: throw RuntimeException()
  }

  fun getAll(): Set<WebContifyCollectionDto> {
    return dslContext
        .select()
        .from(WEBCONTIFY_COLLECTION)
        .leftJoin(WEBCONTIFY_COLLECTION_COLUMN)
        .onKey()
        .asWebcontifyCollectionDtoSet(converter)
  }

  fun deleteById(id: Int?) {
    dslContext
        .deleteFrom(WEBCONTIFY_COLLECTION)
        .where(WEBCONTIFY_COLLECTION.ID.eq(id))
        .execute()
        .let {
          if (it != 1) {
            throw RuntimeException()
          }
        }
  }

  fun update(record: WebContifyCollectionDto): WebContifyCollectionDto {
    return dslContext.newRecord(WEBCONTIFY_COLLECTION).let {
      it.displayName = record.displayName
      it.name = record.name
      it.id = record.id
      it.update()
      return@let converter.convertToDto(it, HashSet())
    }
  }

  fun create(record: WebContifyCollectionDto): WebContifyCollectionDto {
    val collection =
        dslContext.newRecord(WEBCONTIFY_COLLECTION).apply {
          this.displayName = record.displayName
          this.name = record.name
          this.insert()
        }
    val columns =
        record.columns
            ?.map { column ->
              dslContext.newRecord(WEBCONTIFY_COLLECTION_COLUMN).let {
                it.collectionId = collection.id
                it.name = column.name
                it.displayName = column.displayName
                it.type = column.type
                it.isPrimaryKey = column.isPrimaryKey
                it.insert()
                return@let it
              }
            }
            ?.toHashSet()
    return converter.convertToDto(collection, columns ?: HashSet())
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
      converter: CollectionConverter
  ): WebContifyCollectionDto? {
    return this.collectToCollectionMap().firstNotNullOfOrNull { (collection, columns) ->
      converter.convertToDto(collection, columns)
    }
  }

  private fun SelectConnectByStep<Record>.asWebcontifyCollectionDtoSet(
      converter: CollectionConverter
  ): Set<WebContifyCollectionDto> {
    return this.collectToCollectionMap()
        .map { (collection, columns) -> converter.convertToDto(collection, columns) }
        .toHashSet()
  }
}

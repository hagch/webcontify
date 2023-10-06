package io.webcontify.backend.collections.daos

import io.webcontify.backend.collections.mappers.CollectionMapper
import io.webcontify.backend.collections.models.WebContifyCollectionColumnDto
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_COLUMN
import org.jooq.*
import org.springframework.stereotype.Component

@Component
class CollectionColumnDao(val dslContext: DSLContext, val mapper: CollectionMapper) {

  fun getById(collectionId: Int?, name: String?): WebContifyCollectionColumnDto {
    val column =
        dslContext
            .select()
            .from(WEBCONTIFY_COLLECTION_COLUMN)
            .where(
                WEBCONTIFY_COLLECTION_COLUMN.COLLECTION_ID.eq(collectionId)
                    .and(WEBCONTIFY_COLLECTION_COLUMN.NAME.eq(name)))
            .fetchOneInto(WebContifyCollectionColumnDto::class.java)
    return column ?: throw RuntimeException()
  }

  fun getAll(): Set<WebContifyCollectionColumnDto> {
    return dslContext
        .select()
        .from(WEBCONTIFY_COLLECTION_COLUMN)
        .fetchInto(WebContifyCollectionColumnDto::class.java)
        .toHashSet()
  }

  fun getAllForCollection(collectionId: Int?): Set<WebContifyCollectionColumnDto> {
    return dslContext
        .select()
        .from(WEBCONTIFY_COLLECTION_COLUMN)
        .where(WEBCONTIFY_COLLECTION_COLUMN.COLLECTION_ID.eq(collectionId))
        .fetchInto(WebContifyCollectionColumnDto::class.java)
        .toHashSet()
  }

  fun deleteById(collectionId: Int?, name: String?) {
    dslContext
        .deleteFrom(WEBCONTIFY_COLLECTION_COLUMN)
        .where(
            WEBCONTIFY_COLLECTION_COLUMN.COLLECTION_ID.eq(collectionId)
                .and(WEBCONTIFY_COLLECTION_COLUMN.NAME.eq(name)))
        .execute()
        .let {
          if (it != 1) {
            throw RuntimeException()
          }
        }
  }

  fun deleteAllForCollection(collectionId: Int?) {
    dslContext
        .deleteFrom(WEBCONTIFY_COLLECTION_COLUMN)
        .where(WEBCONTIFY_COLLECTION_COLUMN.COLLECTION_ID.eq(collectionId))
        .execute()
  }

  fun update(record: WebContifyCollectionColumnDto): WebContifyCollectionColumnDto {
    return dslContext.newRecord(WEBCONTIFY_COLLECTION_COLUMN).let {
      it.displayName = record.displayName
      it.name = record.name
      it.collectionId = record.collectionId
      it.isPrimaryKey = record.isPrimaryKey
      it.type = record.type
      it.update()
      return@let mapper.mapToDto(it)
    }
  }

  fun create(column: WebContifyCollectionColumnDto): WebContifyCollectionColumnDto {
    return dslContext.newRecord(WEBCONTIFY_COLLECTION_COLUMN).let {
      it.collectionId = column.collectionId
      it.name = column.name
      it.displayName = column.displayName
      it.type = column.type
      it.isPrimaryKey = column.isPrimaryKey
      it.insert()
      return@let mapper.mapToDto(it)
    }
  }
}

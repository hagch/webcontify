package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.mappers.CollectionMapper
import io.webcontify.backend.collections.models.apis.ErrorCode
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_COLUMN
import org.jooq.*
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class CollectionColumnRepository(val dslContext: DSLContext, val mapper: CollectionMapper) {

  @Transactional(readOnly = true)
  fun getById(collectionId: Int?, name: String?): WebContifyCollectionColumnDto {
    val column =
        dslContext
            .select()
            .from(WEBCONTIFY_COLLECTION_COLUMN)
            .where(
                WEBCONTIFY_COLLECTION_COLUMN.COLLECTION_ID.eq(collectionId)
                    .and(WEBCONTIFY_COLLECTION_COLUMN.NAME.eq(name)))
            .fetchOneInto(WebContifyCollectionColumnDto::class.java)
    return column
        ?: throw NotFoundException(
            ErrorCode.COLUMN_NOT_FOUND, name.toString(), collectionId?.toString().toString())
  }

  @Transactional(readOnly = true)
  fun getAllForCollection(collectionId: Int?): Set<WebContifyCollectionColumnDto> {
    return dslContext
        .select()
        .from(WEBCONTIFY_COLLECTION_COLUMN)
        .where(WEBCONTIFY_COLLECTION_COLUMN.COLLECTION_ID.eq(collectionId))
        .fetchInto(WebContifyCollectionColumnDto::class.java)
        .toHashSet()
  }

  @Transactional
  fun deleteById(collectionId: Int?, name: String?) {
    dslContext
        .deleteFrom(WEBCONTIFY_COLLECTION_COLUMN)
        .where(
            WEBCONTIFY_COLLECTION_COLUMN.COLLECTION_ID.eq(collectionId)
                .and(WEBCONTIFY_COLLECTION_COLUMN.NAME.eq(name)))
        .execute()
  }

  @Transactional
  fun deleteAllForCollection(collectionId: Int?) {
    dslContext
        .deleteFrom(WEBCONTIFY_COLLECTION_COLUMN)
        .where(WEBCONTIFY_COLLECTION_COLUMN.COLLECTION_ID.eq(collectionId))
        .execute()
  }

  @Transactional
  fun update(
      record: WebContifyCollectionColumnDto,
      oldName: String
  ): WebContifyCollectionColumnDto {
    val query =
        dslContext
            .update(WEBCONTIFY_COLLECTION_COLUMN)
            .set(WEBCONTIFY_COLLECTION_COLUMN.NAME, record.name)
            .set(WEBCONTIFY_COLLECTION_COLUMN.DISPLAY_NAME, record.displayName)
            .where(
                WEBCONTIFY_COLLECTION_COLUMN.COLLECTION_ID.eq(record.collectionId)
                    .and(WEBCONTIFY_COLLECTION_COLUMN.NAME.eq(oldName)))
    try {
      query.execute().let {
        if (it == 0) {
          throw NotFoundException(
              ErrorCode.COLUMN_NOT_UPDATED, oldName, record.collectionId.toString())
        }
      }
    } catch (e: DuplicateKeyException) {
      throw AlreadyExistsException(
          ErrorCode.COLUMN_WITH_NAME_ALREADY_EXISTS, oldName, record.collectionId.toString())
    }
    return record
  }

  @Transactional
  fun create(column: WebContifyCollectionColumnDto): WebContifyCollectionColumnDto {
    return dslContext.newRecord(WEBCONTIFY_COLLECTION_COLUMN).let {
      it.collectionId = column.collectionId
      it.name = column.name
      it.displayName = column.displayName
      it.type = column.type
      it.isPrimaryKey = column.isPrimaryKey
      try {
        it.insert()
      } catch (e: DuplicateKeyException) {
        throw AlreadyExistsException(
            ErrorCode.COLUMN_WITH_NAME_ALREADY_EXISTS, column.name, column.collectionId.toString())
      } catch (e: DataIntegrityViolationException) {
        throw NotFoundException(ErrorCode.COLLECTION_NOT_FOUND, column.collectionId.toString())
      }
      return@let mapper.mapToDto(it)
    }
  }
}

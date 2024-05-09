package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.mappers.CollectionFieldMapper
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldDto
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.jooq.tables.records.WebcontifyCollectionFieldRecord
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_FIELD
import org.jooq.DSLContext
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class CollectionFieldRepository(val dslContext: DSLContext, val mapper: CollectionFieldMapper) {

  @Transactional(readOnly = true)
  fun getByCollectionIdAndName(collectionId: Long?, name: String?): WebContifyCollectionFieldDto {
    val field =
        dslContext
            .select()
            .from(WEBCONTIFY_COLLECTION_FIELD)
            .where(
                WEBCONTIFY_COLLECTION_FIELD.COLLECTION_ID.eq(collectionId)
                    .and(WEBCONTIFY_COLLECTION_FIELD.NAME.eq(name)))
            .fetchOneInto(WebcontifyCollectionFieldRecord::class.java)
    return field?.let { mapper.mapToDto(field) }
        ?: throw NotFoundException(
            ErrorCode.FIELD_NOT_FOUND, name.toString(), collectionId?.toString().toString())
  }

  @Transactional(readOnly = true)
  fun getById(fieldId: Long): WebContifyCollectionFieldDto {
    val field =
        dslContext
            .select()
            .from(WEBCONTIFY_COLLECTION_FIELD)
            .where(WEBCONTIFY_COLLECTION_FIELD.ID.eq(fieldId))
            .fetchOneInto(WebcontifyCollectionFieldRecord::class.java)
    return field?.let { mapper.mapToDto(field) }
        ?: throw NotFoundException(ErrorCode.FIELD_WITH_ID_NOT_FOUND, fieldId.toString())
  }

  @Transactional(readOnly = true)
  fun getAllForCollection(collectionId: Long?): Set<WebContifyCollectionFieldDto> {
    return dslContext
        .select()
        .from(WEBCONTIFY_COLLECTION_FIELD)
        .where(WEBCONTIFY_COLLECTION_FIELD.COLLECTION_ID.eq(collectionId))
        .fetchInto(WebcontifyCollectionFieldRecord::class.java)
        .toHashSet()
        .map { mapper.mapToDto(it) }
        .toSet()
  }

  @Transactional
  fun deleteById(collectionId: Long?, name: String?) {
    try {
      dslContext
          .deleteFrom(WEBCONTIFY_COLLECTION_FIELD)
          .where(
              WEBCONTIFY_COLLECTION_FIELD.COLLECTION_ID.eq(collectionId)
                  .and(WEBCONTIFY_COLLECTION_FIELD.NAME.eq(name)))
          .execute()
    } catch (e: DataIntegrityViolationException) {
      throw UnprocessableContentException(
          ErrorCode.FIELD_USED_IN_RELATION, name.toString(), collectionId.toString())
    }
  }

  @Transactional
  fun update(record: WebContifyCollectionFieldDto, oldName: String): WebContifyCollectionFieldDto {
    val query =
        dslContext
            .update(WEBCONTIFY_COLLECTION_FIELD)
            .set(WEBCONTIFY_COLLECTION_FIELD.NAME, record.name)
            .set(WEBCONTIFY_COLLECTION_FIELD.DISPLAY_NAME, record.displayName)
            .where(
                WEBCONTIFY_COLLECTION_FIELD.COLLECTION_ID.eq(record.collectionId)
                    .and(WEBCONTIFY_COLLECTION_FIELD.NAME.eq(oldName)))
    try {
      query.execute().let {
        if (it == 0) {
          throw NotFoundException(
              ErrorCode.FIELD_NOT_UPDATED, oldName, record.collectionId.toString())
        }
      }
    } catch (e: DuplicateKeyException) {
      throw AlreadyExistsException(
          ErrorCode.FIELD_WITH_NAME_ALREADY_EXISTS, oldName, record.collectionId.toString())
    } catch (e: DataIntegrityViolationException) {
      throw UnprocessableContentException(
          ErrorCode.FIELD_USED_IN_RELATION, oldName, record.collectionId.toString())
    }
    return record
  }

  @Transactional
  fun create(field: WebContifyCollectionFieldDto): WebContifyCollectionFieldDto {
    return dslContext.newRecord(WEBCONTIFY_COLLECTION_FIELD).let {
      it.collectionId = field.collectionId
      it.name = field.name
      it.displayName = field.displayName
      it.type = field.type
      it.isPrimaryKey = field.isPrimaryKey
      it.configuration = mapper.mapConfigurationToPersistence(field)
      try {
        it.insert()
      } catch (e: DuplicateKeyException) {
        throw AlreadyExistsException(
            ErrorCode.FIELD_WITH_NAME_ALREADY_EXISTS, field.name, field.collectionId.toString())
      } catch (e: DataIntegrityViolationException) {
        throw NotFoundException(ErrorCode.COLLECTION_NOT_FOUND, field.collectionId.toString())
      }
      return@let mapper.mapToDto(it)
    }
  }
}
